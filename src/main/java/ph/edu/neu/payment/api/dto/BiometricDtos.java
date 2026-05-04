package ph.edu.neu.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class BiometricDtos {

    private BiometricDtos() {}

    public record EnrollRequest(
            @NotBlank @Size(max = 120) String deviceId,
            @NotBlank String publicKeyPem,
            String keyAlgorithm) {}

    public record EnrollResponse(UUID credentialId, OffsetDateTime enrolledAt) {}

    public record ChallengeRequest(
            @NotBlank @Size(max = 120) String deviceId,
            @NotBlank @Size(max = 40) String purpose) {}

    public record ChallengeResponse(
            String challenge,
            OffsetDateTime expiresAt) {}

    public record VerifyRequest(
            @NotBlank @Size(max = 120) String deviceId,
            @NotBlank String challenge,
            @NotBlank String signatureBase64) {}

    public record VerifyResponse(
            String stepUpToken,
            OffsetDateTime expiresAt) {}
}
