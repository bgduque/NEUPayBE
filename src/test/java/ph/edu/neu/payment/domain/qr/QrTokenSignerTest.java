package ph.edu.neu.payment.domain.qr;

import org.junit.jupiter.api.Test;

import ph.edu.neu.payment.config.AppProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrTokenSignerTest {

    private static AppProperties props(String hmac) {
        return new AppProperties(
                new AppProperties.Security(
                        new AppProperties.Jwt(
                                Base64.getEncoder().encodeToString(new byte[64]),
                                Duration.ofMinutes(15),
                                Duration.ofDays(30),
                                Duration.ofMinutes(5),
                                "neu-payment"),
                        12,
                        new AppProperties.Cors(List.of("*"))),
                new AppProperties.Qr(hmac, Duration.ofMinutes(2),
                        new AppProperties.Image(360, 2)),
                new AppProperties.Payment(new BigDecimal("50000"), new BigDecimal("20000")),
                new AppProperties.Audit(true),
                null);
    }

    @Test
    void signAndVerifyRoundTrip() {
        String key = Base64.getEncoder().encodeToString(new byte[48]);
        QrTokenSigner signer = new QrTokenSigner(props(key));
        long expires = System.currentTimeMillis() / 1000 + 120;
        var p = new QrTokenSigner.QrPayload(QrMode.PAY_OUT, "abc", "n123", expires);
        String token = signer.sign(p);

        QrTokenSigner.QrPayload parsed = signer.verify(token);
        assertThat(parsed.mode()).isEqualTo(QrMode.PAY_OUT);
        assertThat(parsed.userId()).isEqualTo("abc");
        assertThat(parsed.nonce()).isEqualTo("n123");
        assertThat(parsed.expiresAtEpochSeconds()).isEqualTo(expires);
    }

    @Test
    void rejectsTamperedToken() {
        String key = Base64.getEncoder().encodeToString(new byte[48]);
        QrTokenSigner signer = new QrTokenSigner(props(key));
        var p = new QrTokenSigner.QrPayload(QrMode.PAY_OUT, "abc", "n", 9_999_999_999L);
        String token = signer.sign(p);

        // Flip a character mid-payload.
        String bad = token.substring(0, token.length() - 4) + "XXXX";
        assertThatThrownBy(() -> signer.verify(bad)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsShortHmacSecret() {
        assertThatThrownBy(() -> new QrTokenSigner(props("dG9vc2hvcnQ=")))
                .isInstanceOf(IllegalStateException.class);
    }
}
