package ph.edu.neu.payment.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ph.edu.neu.payment.auth.RefreshTokenRepository;
import ph.edu.neu.payment.common.idempotency.IdempotencyKeyRepository;
import ph.edu.neu.payment.domain.biometric.BiometricChallengeRepository;
import ph.edu.neu.payment.domain.qr.QrTokenRepository;

import java.time.OffsetDateTime;

/**
 * Single home for periodic database cleanup. Lives in its own class with no
 * implemented interface so Spring uses a CGLIB proxy and {@link Transactional}
 * advice applies on the {@link Scheduled} methods.
 */
@Component
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

    private final QrTokenRepository qrTokens;
    private final BiometricChallengeRepository biometricChallenges;
    private final RefreshTokenRepository refreshTokens;
    private final IdempotencyKeyRepository idempotencyKeys;

    public CleanupScheduler(QrTokenRepository qrTokens,
                            BiometricChallengeRepository biometricChallenges,
                            RefreshTokenRepository refreshTokens,
                            IdempotencyKeyRepository idempotencyKeys) {
        this.qrTokens = qrTokens;
        this.biometricChallenges = biometricChallenges;
        this.refreshTokens = refreshTokens;
        this.idempotencyKeys = idempotencyKeys;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void purgeExpiredQrTokens() {
        int n = qrTokens.deleteExpiredBefore(OffsetDateTime.now().minusHours(2));
        if (n > 0) log.info("Purged {} expired QR tokens", n);
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT5M")
    @Transactional
    public void purgeExpiredBiometricChallenges() {
        int n = biometricChallenges.deleteExpiredBefore(OffsetDateTime.now().minusHours(1));
        if (n > 0) log.info("Purged {} expired biometric challenges", n);
    }

    @Scheduled(fixedDelayString = "P1D", initialDelayString = "PT5M")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        int n = refreshTokens.deleteExpiredBefore(OffsetDateTime.now().minusDays(7));
        if (n > 0) log.info("Purged {} expired refresh tokens", n);
    }

    @Scheduled(fixedDelayString = "P1D", initialDelayString = "PT5M")
    @Transactional
    public void purgeOldIdempotencyKeys() {
        int n = idempotencyKeys.deleteOlderThan(OffsetDateTime.now().minusDays(7));
        if (n > 0) log.info("Purged {} stale idempotency keys", n);
    }
}
