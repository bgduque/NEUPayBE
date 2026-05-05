package ph.edu.neu.payment.domain.wallet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ph.edu.neu.payment.domain.user.User;

import java.time.Year;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Idempotent wallet provisioning for any user. Returns the existing wallet
 * if one is already attached, or creates a fresh ACTIVE wallet with a unique
 * NEU card number. Used by user-creation paths and by the backfill runner
 * to repair accounts that were created before wallet provisioning was wired
 * into staff creation.
 */
@Service
public class WalletProvisioner {

    private static final int CARD_NUMBER_RETRIES = 8;
    private static final int VALID_FOR_YEARS = 5;

    private final WalletRepository wallets;

    public WalletProvisioner(WalletRepository wallets) {
        this.wallets = wallets;
    }

    @Transactional
    public Wallet ensureFor(User user) {
        return wallets.findByUser_Id(user.getId()).orElseGet(() -> create(user));
    }

    private Wallet create(User user) {
        Wallet w = new Wallet(user, generateCardNumber(), Year.now().getValue() + VALID_FOR_YEARS);
        return wallets.save(w);
    }

    private String generateCardNumber() {
        for (int i = 0; i < CARD_NUMBER_RETRIES; i++) {
            int a = ThreadLocalRandom.current().nextInt(1000, 10000);
            int b = ThreadLocalRandom.current().nextInt(1000, 10000);
            int c = ThreadLocalRandom.current().nextInt(1000, 10000);
            String card = String.format("NEU-%04d-%04d-%04d", a, b, c);
            if (!wallets.existsByCardNumber(card)) return card;
        }
        throw new IllegalStateException("Unable to allocate a unique wallet card number after retries");
    }
}
