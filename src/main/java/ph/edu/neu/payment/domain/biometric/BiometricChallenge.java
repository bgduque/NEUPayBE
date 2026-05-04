package ph.edu.neu.payment.domain.biometric;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import ph.edu.neu.payment.domain.user.User;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "biometric_challenges")
public class BiometricChallenge {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credential_id")
    private BiometricCredential credential;

    @Column(nullable = false, unique = true, length = 96)
    private String challenge;

    @Column(nullable = false, length = 40)
    private String purpose;

    @CreationTimestamp
    @Column(name = "issued_at", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    protected BiometricChallenge() {}

    public BiometricChallenge(User user, BiometricCredential credential,
                              String challenge, String purpose, OffsetDateTime expiresAt) {
        this.user = user;
        this.credential = credential;
        this.challenge = challenge;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(OffsetDateTime now) { return expiresAt.isBefore(now); }
    public boolean isConsumed() { return consumedAt != null; }
    public void consume(OffsetDateTime at) {
        if (isConsumed()) throw new IllegalStateException("Challenge already consumed");
        this.consumedAt = at;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public BiometricCredential getCredential() { return credential; }
    public String getChallenge() { return challenge; }
    public String getPurpose() { return purpose; }
    public OffsetDateTime getIssuedAt() { return issuedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public OffsetDateTime getConsumedAt() { return consumedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BiometricChallenge other)) return false;
        return id != null && id.equals(other.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
}
