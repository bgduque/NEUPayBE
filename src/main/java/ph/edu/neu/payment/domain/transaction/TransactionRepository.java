package ph.edu.neu.payment.domain.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findTop20ByWallet_IdOrderByOccurredAtDesc(UUID walletId);

    Page<Transaction> findByWallet_IdOrderByOccurredAtDesc(UUID walletId, Pageable pageable);

    Page<Transaction> findByOccurredAtBetweenOrderByOccurredAtDesc(
            OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    boolean existsByReference(String reference);

    @EntityGraph(attributePaths = {"wallet", "wallet.user", "initiatedBy"})
    @Query("select t from Transaction t where (:category is null or t.category = :category)")
    Page<Transaction> findAdminLog(@Param("category") TransactionCategory category, Pageable pageable);

    @Query("select t.occurredAt, u.role, t.amount " +
           "from Transaction t join t.wallet w join w.user u " +
           "where t.category = ph.edu.neu.payment.domain.transaction.TransactionCategory.TOP_UP " +
           "  and t.occurredAt >= :from")
    List<Object[]> rawCashInsSince(@Param("from") OffsetDateTime from);
}
