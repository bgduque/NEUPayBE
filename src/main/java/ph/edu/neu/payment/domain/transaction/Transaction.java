package ph.edu.neu.payment.domain.transaction;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import ph.edu.neu.payment.domain.user.User;
import ph.edu.neu.payment.domain.wallet.Wallet;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counterparty_wallet")
    private Wallet counterpartyWallet;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 160)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionCategory category;

    @Column(nullable = false, unique = true, length = 64)
    private String reference;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "initiated_by_user")
    private User initiatedBy;

    protected Transaction() {}

    public Transaction(Wallet wallet,
                       BigDecimal amount,
                       String title,
                       TransactionCategory category,
                       String reference,
                       BigDecimal balanceAfter,
                       User initiatedBy,
                       Wallet counterpartyWallet) {
        this.wallet = wallet;
        this.amount = amount;
        this.title = title;
        this.category = category;
        this.reference = reference;
        this.balanceAfter = balanceAfter;
        this.initiatedBy = initiatedBy;
        this.counterpartyWallet = counterpartyWallet;
    }

    public UUID getId() { return id; }
    public Wallet getWallet() { return wallet; }
    public Wallet getCounterpartyWallet() { return counterpartyWallet; }
    public BigDecimal getAmount() { return amount; }
    public String getTitle() { return title; }
    public TransactionCategory getCategory() { return category; }
    public String getReference() { return reference; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public User getInitiatedBy() { return initiatedBy; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Transaction other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
