package ph.edu.neu.payment.domain.biometric;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BiometricCredentialRepository extends JpaRepository<BiometricCredential, UUID> {

    Optional<BiometricCredential> findByUser_IdAndDeviceId(UUID userId, String deviceId);

    List<BiometricCredential> findByUser_IdAndRevokedAtIsNull(UUID userId);
}
