package ph.edu.neu.payment.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import ph.edu.neu.payment.config.AppProperties;
import ph.edu.neu.payment.domain.user.UserRole;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtServiceImpl implements JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_STEP_UP = "step_up";

    private final AppProperties props;
    private final SecretKey signingKey;

    public JwtServiceImpl(AppProperties props) {
        this.props = props;
        this.signingKey = decodeKey(props.security().jwt().secret());
    }

    private static SecretKey decodeKey(String secret) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 64) {
            throw new IllegalStateException(
                    "JWT secret too short. Provide >= 64 bytes (Base64-encoded). " +
                    "Generate with: openssl rand -base64 96");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    @Override
    public AccessTokenResult issueAccess(UUID userId, String email, UserRole role, boolean stepUp) {
        var ttl = stepUp ? props.security().jwt().stepUpTtl() : props.security().jwt().accessTokenTtl();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expires = now.plus(ttl);
        String jwt = Jwts.builder()
                .issuer(props.security().jwt().issuer())
                .subject(userId.toString())
                .issuedAt(Date.from(now.toInstant()))
                .expiration(Date.from(expires.toInstant()))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_STEP_UP, stepUp)
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();
        return new AccessTokenResult(jwt, expires);
    }

    @Override
    public ParsedToken parse(String compactJwt) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(props.security().jwt().issuer())
                .build()
                .parseSignedClaims(compactJwt);

        Claims c = jws.getPayload();
        return new ParsedToken(
                UUID.fromString(c.getSubject()),
                c.get(CLAIM_EMAIL, String.class),
                UserRole.valueOf(c.get(CLAIM_ROLE, String.class)),
                Boolean.TRUE.equals(c.get(CLAIM_STEP_UP, Boolean.class)),
                c.getExpiration().toInstant().atOffset(java.time.ZoneOffset.UTC));
    }
}
