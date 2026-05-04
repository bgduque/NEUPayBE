package ph.edu.neu.payment.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import ph.edu.neu.payment.api.dto.AdminDtos;
import ph.edu.neu.payment.api.dto.PaymentDtos;
import ph.edu.neu.payment.api.dto.WalletDtos;
import ph.edu.neu.payment.auth.CurrentUser;
import ph.edu.neu.payment.auth.RequiresStepUp;
import ph.edu.neu.payment.common.idempotency.Idempotent;
import ph.edu.neu.payment.domain.transaction.TransactionCategory;
import ph.edu.neu.payment.domain.transaction.TransactionService;
import ph.edu.neu.payment.domain.user.UserService;
import ph.edu.neu.payment.domain.wallet.WalletService;
import ph.edu.neu.payment.payment.PaymentService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('CASHIER','ADMIN')")
public class AdminController {

    private final UserService userService;
    private final WalletService wallets;
    private final PaymentService payments;
    private final TransactionService transactions;

    public AdminController(UserService userService,
                           WalletService wallets,
                           PaymentService payments,
                           TransactionService transactions) {
        this.userService = userService;
        this.wallets = wallets;
        this.payments = payments;
        this.transactions = transactions;
    }

    @GetMapping("/users")
    public Page<AdminDtos.UserSummary> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return userService.search(q, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))));
    }

    @GetMapping("/users/{id}")
    public AdminDtos.UserDetails userDetails(@PathVariable UUID id) {
        return userService.details(id);
    }

    @GetMapping("/users/{id}/wallet")
    public WalletDtos.WalletView walletForUser(@PathVariable UUID id) {
        return wallets.getWalletForUser(id);
    }

    @PostMapping("/users/{id}/freeze")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDtos.UserDetails freeze(@PathVariable UUID id) {
        return userService.suspend(id, CurrentUser.require().id());
    }

    @PostMapping("/users/{id}/reinstate")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDtos.UserDetails reinstate(@PathVariable UUID id) {
        return userService.reinstate(id, CurrentUser.require().id());
    }

    /** Create a new CASHIER or ADMIN account. */
    @PostMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDtos.UserDetails createStaff(@Valid @RequestBody AdminDtos.CreateStaffRequest req) {
        return userService.createStaff(req, CurrentUser.require().id());
    }

    /**
     * Cashier credits a wallet directly (no QR). Requires step-up biometric auth on
     * the cashier's session — the cashier must have approved their Face ID prompt
     * within the last few minutes.
     */
    @PostMapping("/topup")
    @RequiresStepUp
    @Idempotent
    public PaymentDtos.TransactionResult topup(@Valid @RequestBody PaymentDtos.AdminTopUpRequest req) {
        return payments.adminCredit(CurrentUser.require().id(), req);
    }

    /** Cross-user transaction log. Optional category filter (e.g. TOP_UP for cash-ins only). */
    @GetMapping("/transactions")
    public AdminDtos.TransactionLogPage listTransactions(
            @RequestParam(required = false) TransactionCategory category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return transactions.adminLog(category,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "occurredAt")));
    }

    /** Aggregated cash-in totals/counts grouped by day and recipient role. Drives the chart. */
    @GetMapping("/transactions/stats")
    public AdminDtos.CashInStats cashInStats(@RequestParam(defaultValue = "30") int days) {
        return transactions.cashInStats(days);
    }
}
