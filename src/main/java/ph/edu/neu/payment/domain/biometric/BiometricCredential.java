package ph.edu.neu.payment.domain.biometric;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import ph.edu.neu.payment.domain.user.User;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "biometric_credentials",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "device_id"}))
public class BiometricCredential {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_id", nullable = false, length = 120)
    private String deviceId;

    @Column(name = "public_key_pem", nullable = false, columnDefinition = "text")
    private String publicKeyPem;

    @Column(name = "key_algorithm", nullable = false, length = 40)
    private String keyAlgorithm;

    @CreationTimestamp
    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private OffsetDateTime enrolledAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    protected BiometricCredential() {}

    public BiometricCredential(User user, String deviceId, String publicKeyPem, String keyAlgorithm) {
        this.user = user;
        this.deviceId = deviceId;
        this.publicKeyPem = publicKeyPem;
        this.keyAlgorithm = keyAlgorithm;
    }

    public void revoke(OffsetDateTime at) {
        this.revokedAt = at;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getDeviceId() { return deviceId; }
    public String getPublicKeyPem() { return publicKeyPem; }
    public String getKeyAlgorithm() { return keyAlgorithm; }
    public OffsetDateTime getEnrolledAt() { return enrolledAt; }
    public OffsetDateTime getRevokedAt() { return revokedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BiometricCredential other)) return false;
        return id != null && id.equals(other.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
}
