package ph.edu.neu.payment.common.idempotency;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @Column(name = "idempotency_key", length = 120)
    private String key;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 255)
    private String path;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected IdempotencyKey() {}

    public IdempotencyKey(String key, UUID userId, String method, String path,
                          String responseBody, Integer responseStatus) {
        this.key = key;
        this.userId = userId;
        this.method = method;
        this.path = path;
        this.responseBody = responseBody;
        this.responseStatus = responseStatus;
    }

    public String getKey() { return key; }
    public UUID getUserId() { return userId; }
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public String getResponseBody() { return responseBody; }
    public Integer getResponseStatus() { return responseStatus; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
