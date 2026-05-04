package ph.edu.neu.payment.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import ph.edu.neu.payment.api.dto.WalletDtos;
import ph.edu.neu.payment.auth.CurrentUser;
import ph.edu.neu.payment.domain.transaction.TransactionService;
import ph.edu.neu.payment.domain.wallet.WalletService;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService wallets;
    private final TransactionService transactions;

    public WalletController(WalletService wallets, TransactionService transactions) {
        this.wallets = wallets;
        this.transactions = transactions;
    }

    @GetMapping("/me")
    public WalletDtos.WalletView myWallet() {
        return wallets.getMyWallet(CurrentUser.require().id());
    }

    @GetMapping("/me/transactions")
    public WalletDtos.TransactionPage myTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        return transactions.forUser(CurrentUser.require().id(), PageRequest.of(safePage, safeSize));
    }

    @GetMapping("/me/transactions/recent")
    public java.util.List<WalletDtos.TransactionView> recent(
            @RequestParam(defaultValue = "10") int limit) {
        return transactions.recentForUser(CurrentUser.require().id(), limit);
    }
}
