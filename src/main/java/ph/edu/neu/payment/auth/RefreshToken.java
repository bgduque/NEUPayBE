package ph.edu.neu.payment.auth;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import ph.edu.neu.payment.domain.user.User;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "device_id", length = 120)
    private String deviceId;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected RefreshToken() {}

    public RefreshToken(User user, String tokenHash, String deviceId, OffsetDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.deviceId = deviceId;
        this.expiresAt = expiresAt;
    }

    public void revoke(OffsetDateTime at) {
        if (this.revokedAt == null) this.revokedAt = at;
    }

    public boolean isRevoked() { return revokedAt != null; }

    public boolean isExpired(OffsetDateTime now) { return expiresAt.isBefore(now); }

    public boolean isUsable(OffsetDateTime now) { return !isRevoked() && !isExpired(now); }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public String getDeviceId() { return deviceId; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RefreshToken other)) return false;
        return id != null && id.equals(other.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
}
