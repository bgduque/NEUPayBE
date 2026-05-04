package ph.edu.neu.payment.domain.qr;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import ph.edu.neu.payment.domain.user.User;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "qr_tokens")
public class QrToken {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 64)
    private String nonce;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QrMode mode;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumed_by")
    private User consumedBy;

    protected QrToken() {}

    public QrToken(User user, String nonce, QrMode mode, OffsetDateTime expiresAt) {
        this.user = user;
        this.nonce = nonce;
        this.mode = mode;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(OffsetDateTime now) {
        return expiresAt.isBefore(now);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public void consume(User by, OffsetDateTime at) {
        if (isConsumed()) throw new IllegalStateException("QR already consumed");
        this.consumedBy = by;
        this.consumedAt = at;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getNonce() { return nonce; }
    public QrMode getMode() { return mode; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getConsumedAt() { return consumedAt; }
    public User getConsumedBy() { return consumedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QrToken other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
