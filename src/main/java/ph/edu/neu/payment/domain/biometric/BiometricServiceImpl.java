package ph.edu.neu.payment.domain.biometric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ph.edu.neu.payment.api.dto.BiometricDtos;
import ph.edu.neu.payment.auth.AuthService;
import ph.edu.neu.payment.common.error.BadRequestException;
import ph.edu.neu.payment.common.error.ConflictException;
import ph.edu.neu.payment.common.error.NotFoundException;
import ph.edu.neu.payment.common.error.UnauthorizedException;
import ph.edu.neu.payment.domain.audit.AuditService;
import ph.edu.neu.payment.domain.user.User;
import ph.edu.neu.payment.domain.user.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Server-side half of the iOS Face ID 2FA flow.
 *
 * Enrolment:
 *   1. iOS generates a P-256 keypair in the Secure Enclave (ECC, gated by Face ID).
 *   2. App calls {@link #enroll} with the public key (X.509/SubjectPublicKeyInfo, PEM-encoded).
 *
 * Step-up auth:
 *   1. App calls {@link #issueChallenge} → server returns a random nonce + ttl.
 *   2. iOS prompts Face ID; on success the Secure Enclave signs the nonce (ECDSA P-256, SHA-256).
 *   3. App calls {@link #verify} with the signature (DER, base64). Server verifies, marks
 *      the challenge consumed, and mints a short-lived step-up JWT.
 */
@Service
public class BiometricServiceImpl implements BiometricService {

    private static final Logger log = LoggerFactory.getLogger(BiometricServiceImpl.class);
    private static final SecureRandom RNG = new SecureRandom();
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(2);
    private static final String DEFAULT_ALGO = "EC_SECP256R1";

    private final BiometricCredentialRepository credentials;
    private final BiometricChallengeRepository challenges;
    private final UserRepository users;
    private final AuthService authService;
    private final AuditService audit;

    public BiometricServiceImpl(BiometricCredentialRepository credentials,
                                BiometricChallengeRepository challenges,
                                UserRepository users,
                                AuthService authService,
                                AuditService audit) {
        this.credentials = credentials;
        this.challenges = challenges;
        this.users = users;
        this.authService = authService;
        this.audit = audit;
    }

    @Override
    @Transactional
    public BiometricDtos.EnrollResponse enroll(UUID userId, BiometricDtos.EnrollRequest req) {
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (credentials.findByUser_IdAndDeviceId(userId, req.deviceId())
                .filter(BiometricCredential::isActive)
                .isPresent()) {
            throw new ConflictException("Device already enrolled");
        }

        String algo = req.keyAlgorithm() == null ? DEFAULT_ALGO : req.keyAlgorithm();
        // Validate the PEM is parseable now so we don't accept junk.
        decodePublicKey(req.publicKeyPem(), algo);

        BiometricCredential c = new BiometricCredential(user, req.deviceId(), req.publicKeyPem(), algo);
        credentials.save(c);

        audit.record(userId, "BIOMETRIC_ENROLL", "BiometricCredential", c.getId().toString(), null);
        return new BiometricDtos.EnrollResponse(c.getId(), c.getEnrolledAt());
    }

    @Override
    @Transactional
    public BiometricDtos.ChallengeResponse issueChallenge(UUID userId, BiometricDtos.ChallengeRequest req) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        BiometricCredential cred = credentials.findByUser_IdAndDeviceId(userId, req.deviceId())
                .filter(BiometricCredential::isActive)
                .orElseThrow(() -> new NotFoundException("No biometric credential enrolled for device"));

        String challenge = randomChallenge();
        OffsetDateTime expires = OffsetDateTime.now().plus(CHALLENGE_TTL);
        challenges.save(new BiometricChallenge(user, cred, challenge, req.purpose(), expires));
        return new BiometricDtos.ChallengeResponse(challenge, expires);
    }

    @Override
    @Transactional
    public BiometricDtos.VerifyResponse verify(UUID userId, BiometricDtos.VerifyRequest req) {
        BiometricChallenge ch = challenges.findByChallenge(req.challenge())
                .orElseThrow(() -> new UnauthorizedException("Invalid challenge"));

        if (!ch.getUser().getId().equals(userId)) throw new UnauthorizedException("Challenge mismatch");
        if (ch.isConsumed()) throw new UnauthorizedException("Challenge already used");
        OffsetDateTime now = OffsetDateTime.now();
        if (ch.isExpired(now)) throw new UnauthorizedException("Challenge expired");

        BiometricCredential cred = credentials.findByUser_IdAndDeviceId(userId, req.deviceId())
                .filter(BiometricCredential::isActive)
                .orElseThrow(() -> new UnauthorizedException("Credential not found"));

        if (ch.getCredential() != null && !ch.getCredential().getId().equals(cred.getId())) {
            throw new UnauthorizedException("Credential mismatch");
        }

        if (!verifySignature(cred, req.challenge(), req.signatureBase64())) {
            audit.record(userId, "BIOMETRIC_VERIFY_FAILED",
                    "BiometricCredential", cred.getId().toString(), null);
            throw new UnauthorizedException("Signature invalid");
        }

        ch.consume(now);
        audit.record(userId, "BIOMETRIC_VERIFY_OK",
                "BiometricCredential", cred.getId().toString(), ch.getPurpose());

        var stepUp = authService.issueStepUp(
                ch.getUser().getId(), ch.getUser().getEmail(), ch.getUser().getRole());
        return new BiometricDtos.VerifyResponse(stepUp.accessToken(), stepUp.expiresAt());
    }

    @Override
    @Transactional
    public void revoke(UUID userId, String deviceId) {
        credentials.findByUser_IdAndDeviceId(userId, deviceId)
                .filter(BiometricCredential::isActive)
                .ifPresent(c -> {
                    c.revoke(OffsetDateTime.now());
                    audit.record(userId, "BIOMETRIC_REVOKE", "BiometricCredential", c.getId().toString(), null);
                });
    }

    private boolean verifySignature(BiometricCredential cred, String challenge, String signatureBase64) {
        try {
            PublicKey pub = decodePublicKey(cred.getPublicKeyPem(), cred.getKeyAlgorithm());
            Signature sig = Signature.getInstance("SHA256withECDSA");
            sig.initVerify(pub);
            sig.update(challenge.getBytes(StandardCharsets.UTF_8));
            byte[] der = Base64.getDecoder().decode(signatureBase64);
            return sig.verify(der);
        } catch (Exception ex) {
            log.warn("ECDSA verify failed: {}", ex.toString());
            return false;
        }
    }

    private static PublicKey decodePublicKey(String pem, String algo) {
        try {
            String body = pem
                    .replaceAll("-----BEGIN [^-]+-----", "")
                    .replaceAll("-----END [^-]+-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(body);
            String keyAlg = algo.startsWith("EC") ? "EC" : "RSA";
            return KeyFactory.getInstance(keyAlg).generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new BadRequestException("Invalid public key encoding");
        }
    }

    private static String randomChallenge() {
        byte[] buf = new byte[36];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

}
