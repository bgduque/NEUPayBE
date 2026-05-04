package ph.edu.neu.payment.domain.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ph.edu.neu.payment.api.dto.AdminDtos;
import ph.edu.neu.payment.api.dto.WalletDtos;
import ph.edu.neu.payment.common.error.NotFoundException;
import ph.edu.neu.payment.domain.user.User;
import ph.edu.neu.payment.domain.user.UserRole;
import ph.edu.neu.payment.domain.wallet.Wallet;
import ph.edu.neu.payment.domain.wallet.WalletRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactions;
    private final WalletRepository wallets;

    public TransactionServiceImpl(TransactionRepository transactions, WalletRepository wallets) {
        this.transactions = transactions;
        this.wallets = wallets;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WalletDtos.TransactionView> recentForUser(UUID userId, int limit) {
        Wallet w = walletForUser(userId);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        Page<Transaction> page = transactions.findByWallet_IdOrderByOccurredAtDesc(
                w.getId(), PageRequest.of(0, safeLimit));
        return page.map(TransactionServiceImpl::view).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public WalletDtos.TransactionPage forUser(UUID userId, Pageable pageable) {
        Wallet w = walletForUser(userId);
        Page<Transaction> p = transactions.findByWallet_IdOrderByOccurredAtDesc(w.getId(), pageable);
        return new WalletDtos.TransactionPage(
                p.map(TransactionServiceImpl::view).getContent(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements());
    }

    private Wallet walletForUser(UUID userId) {
        return wallets.findByUser_Id(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found"));
    }

    static WalletDtos.TransactionView view(Transaction t) {
        return new WalletDtos.TransactionView(
                t.getId(),
                t.getTitle(),
                t.getAmount(),
                t.getCategory(),
                t.getReference(),
                t.getBalanceAfter(),
                t.getOccurredAt());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDtos.TransactionLogPage adminLog(TransactionCategory category, Pageable pageable) {
        Page<Transaction> p = transactions.findAdminLog(category, pageable);
        List<AdminDtos.TransactionLogEntry> rows = p.getContent().stream()
                .map(TransactionServiceImpl::logEntry)
                .toList();
        return new AdminDtos.TransactionLogPage(rows, p.getNumber(), p.getSize(), p.getTotalElements());
    }

    private static AdminDtos.TransactionLogEntry logEntry(Transaction t) {
        User recipient = t.getWallet().getUser();
        User cashier = t.getInitiatedBy();
        return new AdminDtos.TransactionLogEntry(
                t.getId(),
                t.getReference(),
                t.getTitle(),
                t.getAmount(),
                t.getBalanceAfter(),
                t.getCategory(),
                t.getOccurredAt(),
                recipient.getId(),
                recipient.getFullName(),
                recipient.getRole(),
                cashier == null ? null : cashier.getId(),
                cashier == null ? null : cashier.getFullName());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDtos.CashInStats cashInStats(int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).minusDays(safeDays).withHour(0).withMinute(0).withSecond(0).withNano(0);

        record Key(LocalDate date, UserRole role) {}
        record Agg(long count, BigDecimal total) {
            Agg add(BigDecimal amount) { return new Agg(count + 1, total.add(amount)); }
        }
        Map<Key, Agg> groups = new HashMap<>();

        for (Object[] row : transactions.rawCashInsSince(from)) {
            OffsetDateTime occurredAt = (OffsetDateTime) row[0];
            UserRole role = (UserRole) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            Key k = new Key(occurredAt.atZoneSameInstant(ZoneOffset.UTC).toLocalDate(), role);
            groups.merge(k, new Agg(1, amount), (a, b) -> a.add(b.total));
        }

        List<AdminDtos.CashInStatsBucket> buckets = new ArrayList<>(groups.size());
        groups.forEach((k, v) -> buckets.add(
                new AdminDtos.CashInStatsBucket(k.date(), k.role(), v.count(), v.total())));
        buckets.sort((a, b) -> {
            int c = a.date().compareTo(b.date());
            return c != 0 ? c : a.role().compareTo(b.role());
        });
        return new AdminDtos.CashInStats(safeDays, buckets);
    }
}
