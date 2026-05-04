package ph.edu.neu.payment.domain.transaction;

import org.springframework.data.domain.Pageable;

import ph.edu.neu.payment.api.dto.AdminDtos;
import ph.edu.neu.payment.api.dto.WalletDtos;

import java.util.List;
import java.util.UUID;

public interface TransactionService {

    List<WalletDtos.TransactionView> recentForUser(UUID userId, int limit);

    WalletDtos.TransactionPage forUser(UUID userId, Pageable pageable);

    /** Cross-user transaction log for the admin / cashier dashboard. */
    AdminDtos.TransactionLogPage adminLog(TransactionCategory category, Pageable pageable);

    /** Aggregated cash-in counts and totals by day + recipient role for the chart. */
    AdminDtos.CashInStats cashInStats(int days);
}
