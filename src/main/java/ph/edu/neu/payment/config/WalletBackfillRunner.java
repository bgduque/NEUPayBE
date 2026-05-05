package ph.edu.neu.payment.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import ph.edu.neu.payment.domain.user.User;
import ph.edu.neu.payment.domain.user.UserRepository;
import ph.edu.neu.payment.domain.wallet.WalletProvisioner;
import ph.edu.neu.payment.domain.wallet.WalletRepository;

/**
 * One-shot startup pass: any User without a Wallet gets one provisioned. This
 * exists because earlier `createStaff` and `BootstrapAdminRunner` paths saved
 * users without creating wallets, so admin/staff accounts 404'd on the wallet
 * lookup. Runs idempotently on every boot — safe to leave enabled.
 */
@Configuration
public class WalletBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(WalletBackfillRunner.class);

    @Bean
    public ApplicationRunner backfillWallets(UserRepository users,
                                             WalletRepository wallets,
                                             WalletProvisioner provisioner) {
        return args -> backfill(users, wallets, provisioner);
    }

    @Transactional
    void backfill(UserRepository users, WalletRepository wallets, WalletProvisioner provisioner) {
        int created = 0;
        for (User u : users.findAll()) {
            if (wallets.findByUser_Id(u.getId()).isEmpty()) {
                provisioner.ensureFor(u);
                created++;
            }
        }
        if (created > 0) {
            log.info("Backfilled {} missing wallet(s) on startup.", created);
        }
    }
}
