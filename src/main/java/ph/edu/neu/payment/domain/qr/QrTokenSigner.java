package ph.edu.neu.payment.domain.qr;

import org.springframework.stereotype.Component;

import ph.edu.neu.payment.config.AppProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Produces a tamper-evident payload for QR tokens. The QR scanner can read the
 * payload directly; the server validates it by recomputing the HMAC.
 *
 * Payload format (Base64URL): {@code <body>.<sig>}
 * where body = {@code v1|<mode>|<userId>|<nonce>|<expiresAtEpochSeconds>}
 */
@Component
public class QrTokenSigner {

    private static final String VERSION = "v1";

    private final byte[] secret;

    public QrTokenSigner(AppProperties props) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(props.qr().hmacSecret());
        } catch (IllegalArgumentException ex) {
            raw = props.qr().hmacSecret().getBytes(StandardCharsets.UTF_8);
        }
        if (raw.length < 32) {
            throw new IllegalStateException(
                    "QR HMAC secret too short. Provide >= 32 bytes (Base64-encoded). " +
                    "Generate with: openssl rand -base64 48");
        }
        this.secret = raw;
    }

    public String sign(QrPayload payload) {
        String body = body(payload);
        String sig = hmac(body);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((body + "." + sig).getBytes(StandardCharsets.UTF_8));
    }

    public QrPayload verify(String compact) {
        String decoded = new String(Base64.getUrlDecoder().decode(compact), StandardCharsets.UTF_8);
        int dot = decoded.lastIndexOf('.');
        if (dot < 0) throw new IllegalArgumentException("Invalid token");
        String body = decoded.substring(0, dot);
        String sig = decoded.substring(dot + 1);

        String expected = hmac(body);
        if (!constantTimeEquals(sig, expected)) {
            throw new IllegalArgumentException("Invalid signature");
        }
        return parseBody(body);
    }

    private String body(QrPayload p) {
        return String.join("|",
                VERSION,
                p.mode().name(),
                p.userId(),
                p.nonce(),
                Long.toString(p.expiresAtEpochSeconds()));
    }

    private QrPayload parseBody(String body) {
        String[] parts = body.split("\\|");
        if (parts.length != 5 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Unsupported token version");
        }
        return new QrPayload(
                QrMode.valueOf(parts[1]),
                parts[2],
                parts[3],
                Long.parseLong(parts[4]));
    }

    private String hmac(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    public record QrPayload(QrMode mode, String userId, String nonce, long expiresAtEpochSeconds) {}
}
