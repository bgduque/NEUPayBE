package ph.edu.neu.payment.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.security.crypto.password.PasswordEncoder;

import ph.edu.neu.payment.api.dto.AdminDtos;
import ph.edu.neu.payment.common.error.ConflictException;
import ph.edu.neu.payment.common.error.NotFoundException;
import ph.edu.neu.payment.domain.audit.AuditService;
import ph.edu.neu.payment.domain.wallet.Wallet;
import ph.edu.neu.payment.domain.wallet.WalletRepository;
import ph.edu.neu.payment.domain.wallet.WalletService;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository users;
    private final WalletRepository wallets;
    private final WalletService walletService;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository users,
                           WalletRepository wallets,
                           WalletService walletService,
                           AuditService audit,
                           PasswordEncoder passwordEncoder) {
        this.users = users;
        this.wallets = wallets;
        this.walletService = walletService;
        this.audit = audit;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminDtos.UserSummary> search(String query, Pageable pageable) {
        Page<User> page = (query == null || query.isBlank())
                ? users.findAll(pageable)
                : users.search(query.trim(), pageable);
        return page.map(u -> new AdminDtos.UserSummary(
                u.getId(), u.getFullName(), u.getEmail(), u.getIdNumber(), u.getRole(), u.getStatus()));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDtos.UserDetails details(UUID userId) {
        User u = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Wallet w = wallets.findByUser_Id(userId).orElse(null);
        BigDecimal balance = w == null ? BigDecimal.ZERO : w.getBalance();
        String card = w == null ? null : w.getCardNumber();
        return new AdminDtos.UserDetails(u.getId(), u.getFullName(), u.getEmail(),
                u.getIdNumber(), u.getProgram(), u.getRole(), u.getStatus(),
                balance, card, u.getCreatedAt(), u.getLastLoginAt());
    }

    @Override
    @Transactional
    public AdminDtos.UserDetails suspend(UUID userId, UUID actorUserId) {
        User u = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        u.updateStatus(VerificationStatus.SUSPENDED);
        wallets.findByUser_Id(userId).ifPresent(w -> walletService.freeze(w.getId()));
        audit.record(actorUserId, "USER_SUSPEND", "User", userId.toString(), null);
        return details(userId);
    }

    @Override
    @Transactional
    public AdminDtos.UserDetails reinstate(UUID userId, UUID actorUserId) {
        User u = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        u.updateStatus(VerificationStatus.VERIFIED);
        wallets.findByUser_Id(userId).ifPresent(w -> walletService.activate(w.getId()));
        audit.record(actorUserId, "USER_REINSTATE", "User", userId.toString(), null);
        return details(userId);
    }

    @Override
    @Transactional
    public AdminDtos.UserDetails createStaff(AdminDtos.CreateStaffRequest req, UUID actorUserId) {
        if (users.existsByEmailIgnoreCase(req.email()))
            throw new ConflictException("Email already registered");
        if (users.existsByIdNumber(req.idNumber()))
            throw new ConflictException("ID number already registered");

        UserRole role = switch (req.role()) {
            case CASHIER -> UserRole.CASHIER;
            case ADMIN   -> UserRole.ADMIN;
        };

        User staff = new User(
                req.fullName(),
                req.email().toLowerCase(),
                req.idNumber(),
                "Staff",
                role,
                VerificationStatus.VERIFIED,
                passwordEncoder.encode(req.temporaryPassword()));
        users.save(staff);
        audit.record(actorUserId, "STAFF_CREATE", "User", staff.getId().toString(), role.name());
        return details(staff.getId());
    }
}
