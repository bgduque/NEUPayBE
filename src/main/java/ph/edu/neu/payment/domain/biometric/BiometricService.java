package ph.edu.neu.payment.domain.biometric;

import ph.edu.neu.payment.api.dto.BiometricDtos;

import java.util.UUID;

public interface BiometricService {

    BiometricDtos.EnrollResponse enroll(UUID userId, BiometricDtos.EnrollRequest req);

    BiometricDtos.ChallengeResponse issueChallenge(UUID userId, BiometricDtos.ChallengeRequest req);

    BiometricDtos.VerifyResponse verify(UUID userId, BiometricDtos.VerifyRequest req);

    /** Revoke a device's biometric credential (e.g. user logged out / lost phone). */
    void revoke(UUID userId, String deviceId);
}
