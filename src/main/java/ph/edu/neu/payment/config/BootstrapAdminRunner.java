package ph.edu.neu.payment.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import ph.edu.neu.payment.domain.user.User;
import ph.edu.neu.payment.domain.user.UserRepository;
import ph.edu.neu.payment.domain.user.UserRole;
import ph.edu.neu.payment.domain.user.VerificationStatus;
import ph.edu.neu.payment.domain.wallet.WalletProvisioner;

/**
 * On startup, if no ADMIN exists yet AND the bootstrap env vars are set, provision
 * a single ADMIN account so the cashier admin-panel can be accessed for the first
 * time. After that admin signs in once, they create more staff accounts via the
 * admin endpoint and the bootstrap env vars should be removed.
 */
@Configuration
public class BootstrapAdminRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    @Bean
    public ApplicationRunner bootstrapAdmin(UserRepository users,
                                            PasswordEncoder encoder,
                                            AppProperties props,
                                            WalletProvisioner walletProvisioner) {
        return args -> {
            provision(users, encoder, props, walletProvisioner);
            provisionSecondary(users, encoder, props, walletProvisioner);
        };
    }

    @Transactional
    void provision(UserRepository users, PasswordEncoder encoder, AppProperties props,
                   WalletProvisioner walletProvisioner) {
        var bootstrap = props.bootstrap();
        if (bootstrap == null
                || isBlank(bootstrap.adminEmail())
                || isBlank(bootstrap.adminPassword())) {
            return;
        }

        if (users.findByEmailIgnoreCase(bootstrap.adminEmail()).isPresent()) {
            return;
        }

        // Don't create a duplicate ID number; if collision, skip and warn.
        if (users.existsByIdNumber(bootstrap.adminIdNumber())) {
            log.warn("Bootstrap admin ID number already exists; skipping provisioning.");
            return;
        }

        User admin = new User(
                bootstrap.adminFullName(),
                bootstrap.adminEmail().toLowerCase(),
                bootstrap.adminIdNumber(),
                "Administration",
                UserRole.ADMIN,
                VerificationStatus.VERIFIED,
                encoder.encode(bootstrap.adminPassword()));
        users.save(admin);
        walletProvisioner.ensureFor(admin);
        log.info("Bootstrap ADMIN provisioned: {} (id-number {})",
                bootstrap.adminEmail(), bootstrap.adminIdNumber());
    }

    @Transactional
    void provisionSecondary(UserRepository users, PasswordEncoder encoder, AppProperties props,
                            WalletProvisioner walletProvisioner) {
        var bootstrap = props.bootstrap();
        if (bootstrap == null || bootstrap.secondary() == null) return;
        var s = bootstrap.secondary();

        if (isBlank(s.email()) || isBlank(s.password())
                || isBlank(s.idNumber()) || isBlank(s.fullName())) {
            return;
        }
        if (users.findByEmailIgnoreCase(s.email()).isPresent()) return;
        if (users.existsByIdNumber(s.idNumber())) {
            log.warn("Secondary admin ID number {} already exists; skipping provisioning.", s.idNumber());
            return;
        }

        User admin = new User(
                s.fullName(),
                s.email().toLowerCase(),
                s.idNumber(),
                "Administration",
                UserRole.ADMIN,
                VerificationStatus.VERIFIED,
                encoder.encode(s.password()));
        users.save(admin);
        walletProvisioner.ensureFor(admin);
        log.info("Secondary ADMIN provisioned: {} (id-number {})", s.email(), s.idNumber());
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
