package ph.edu.neu.payment.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "neu")
public record AppProperties(
        @Valid @NotNull Security security,
        @Valid @NotNull Qr qr,
        @Valid @NotNull Payment payment,
        @Valid @NotNull Audit audit,
        Bootstrap bootstrap) {

    public record Security(
            @Valid @NotNull Jwt jwt,
            int bcryptStrength,
            @Valid @NotNull Cors cors) {
    }

    public record Jwt(
            @NotBlank String secret,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl,
            @NotNull Duration stepUpTtl,
            @NotBlank String issuer) {
    }

    public record Cors(@NotNull List<String> allowedOrigins) {
    }

    public record Qr(
            @NotBlank String hmacSecret,
            @NotNull Duration rotationWindow,
            @Valid @NotNull Image image) {
    }

    public record Image(int sizePx, int margin) {
    }

    public record Payment(
            @NotNull BigDecimal maxSingleTopup,
            @NotNull BigDecimal maxSinglePayment) {
    }

    public record Audit(boolean enabled) {
    }

    public record Bootstrap(
            String adminEmail,
            String adminPassword,
            String adminIdNumber,
            String adminFullName) {
    }
}
