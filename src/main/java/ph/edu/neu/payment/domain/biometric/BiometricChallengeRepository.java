package ph.edu.neu.payment.domain.biometric;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface BiometricChallengeRepository extends JpaRepository<BiometricChallenge, UUID> {

    Optional<BiometricChallenge> findByChallenge(String challenge);

    @Modifying
    @Query("delete from BiometricChallenge c where c.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
