package ph.edu.neu.payment.domain.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByActorUserIdOrderByOccurredAtDesc(UUID actorUserId, Pageable pageable);
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDesc(String entityType, String entityId, Pageable pageable);
}
