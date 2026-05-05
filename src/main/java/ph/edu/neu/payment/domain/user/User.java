package ph.edu.neu.payment.domain.user;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "id_number", nullable = false, unique = true, length = 32)
    private String idNumber;

    @Column(length = 160)
    private String program;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus status;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected User() {}

    public User(String fullName, String email, String idNumber, String program,
                UserRole role, VerificationStatus status, String passwordHash) {
        this.fullName = fullName;
        this.email = email;
        this.idNumber = idNumber;
        this.program = program;
        this.role = role;
        this.status = status;
        this.passwordHash = passwordHash;
    }

    public void recordLogin(OffsetDateTime at) {
        this.lastLoginAt = at;
    }

    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public void updateStatus(VerificationStatus newStatus) {
        this.status = newStatus;
    }

    public void changeRole(UserRole newRole) {
        this.role = newRole;
    }

    public void changeProgram(String newProgram) {
        this.program = newProgram;
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getIdNumber() { return idNumber; }
    public String getProgram() { return program; }
    public UserRole getRole() { return role; }
    public VerificationStatus getStatus() { return status; }
    public String getPasswordHash() { return passwordHash; }
    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
