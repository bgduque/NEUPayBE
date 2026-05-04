package ph.edu.neu.payment.domain.qr;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface QrTokenRepository extends JpaRepository<QrToken, UUID> {

    Optional<QrToken> findByNonce(String nonce);

    /**
     * Locks the row for the duration of the surrounding transaction so that two
     * cashier scans of the same nonce can't both pass {@code isConsumed()} before
     * either writes. Use this on the redeem path.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QrToken q where q.nonce = :nonce")
    Optional<QrToken> findByNonceForUpdate(@Param("nonce") String nonce);

    @Modifying
    @Query("delete from QrToken q where q.expiresAt < :cutoff and q.consumedAt is null")
    int deleteExpiredBefore(@Param("cutoff") OffsetDateTime cutoff);
}
