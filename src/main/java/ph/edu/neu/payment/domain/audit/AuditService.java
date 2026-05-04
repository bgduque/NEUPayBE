package ph.edu.neu.payment.domain.audit;

import java.util.UUID;

public interface AuditService {

    void record(UUID actorUserId, String action, String entityType, String entityId, String details);
}
