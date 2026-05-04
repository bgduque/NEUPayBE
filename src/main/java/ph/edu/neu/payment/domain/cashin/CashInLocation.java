package ph.edu.neu.payment.domain.cashin;

import jakarta.persistence.*;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "cash_in_locations")
public class CashInLocation {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 255)
    private String sublabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashInKind kind;

    @Column(nullable = false)
    private boolean active = true;

    protected CashInLocation() {}

    public CashInLocation(String name, String sublabel, CashInKind kind) {
        this.name = name;
        this.sublabel = sublabel;
        this.kind = kind;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSublabel() { return sublabel; }
    public CashInKind getKind() { return kind; }
    public boolean isActive() { return active; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CashInLocation other)) return false;
        return id != null && id.equals(other.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
}
