package ph.edu.neu.payment.domain.wallet;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import ph.edu.neu.payment.domain.user.User;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "card_number", nullable = false, unique = true, length = 32)
    private String cardNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletStatus status;

    @Column(name = "valid_until_year", nullable = false)
    private int validUntilYear;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Wallet() {}

    public Wallet(User user, String cardNumber, int validUntilYear) {
        this.user = user;
        this.cardNumber = cardNumber;
        this.validUntilYear = validUntilYear;
        this.balance = BigDecimal.ZERO;
        this.status = WalletStatus.ACTIVE;
    }

    /**
     * Apply a signed delta to the balance. Negative deltas debit the wallet
     * and require the wallet to be ACTIVE with sufficient funds. Returns the
     * new balance.
     */
    public BigDecimal applyDelta(BigDecimal delta) {
        Objects.requireNonNull(delta, "delta");
        if (status != WalletStatus.ACTIVE) {
            throw new IllegalStateException("Wallet not ACTIVE: " + status);
        }
        BigDecimal next = balance.add(delta);
        if (next.signum() < 0) {
            throw new IllegalStateException("Insufficient funds");
        }
        this.balance = next;
        return next;
    }

    public void freeze()  { this.status = WalletStatus.FROZEN; }
    public void activate() { this.status = WalletStatus.ACTIVE; }
    public void close()    { this.status = WalletStatus.CLOSED; }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public BigDecimal getBalance() { return balance; }
    public String getCardNumber() { return cardNumber; }
    public WalletStatus getStatus() { return status; }
    public int getValidUntilYear() { return validUntilYear; }
    public long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Wallet other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
