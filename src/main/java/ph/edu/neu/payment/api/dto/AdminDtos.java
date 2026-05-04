package ph.edu.neu.payment.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import ph.edu.neu.payment.domain.transaction.TransactionCategory;
import ph.edu.neu.payment.domain.user.UserRole;
import ph.edu.neu.payment.domain.user.VerificationStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AdminDtos {

    private AdminDtos() {}

    public record CreateStaffRequest(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 6, max = 32) @Pattern(regexp = "[A-Za-z0-9-]+") String idNumber,
            @NotBlank @Size(min = 12, max = 128) String temporaryPassword,
            @NotNull StaffRole role) {

        public enum StaffRole { CASHIER, ADMIN }
    }

    public record UserSummary(
            UUID id,
            String fullName,
            String email,
            String idNumber,
            UserRole role,
            VerificationStatus status) {}

    public record UserDetails(
            UUID id,
            String fullName,
            String email,
            String idNumber,
            String program,
            UserRole role,
            VerificationStatus status,
            BigDecimal walletBalance,
            String walletCardNumber,
            OffsetDateTime createdAt,
            OffsetDateTime lastLoginAt) {}

    /**
     * One row in the dashboard transaction log. Embeds both the recipient
     * (the wallet owner) and the cashier (initiator) so the FE can render
     * "{recipient} — Cashed in {amount} by {cashier}" without N+1 fetches.
     */
    public record TransactionLogEntry(
            UUID id,
            String reference,
            String title,
            BigDecimal amount,
            BigDecimal balanceAfter,
            TransactionCategory category,
            OffsetDateTime occurredAt,
            UUID recipientId,
            String recipientName,
            UserRole recipientRole,
            UUID cashierId,
            String cashierName) {}

    public record TransactionLogPage(
            List<TransactionLogEntry> items,
            int page,
            int size,
            long totalElements) {}

    /** A single (date, role) bucket for the cash-in line chart. */
    public record CashInStatsBucket(
            LocalDate date,
            UserRole role,
            long count,
            BigDecimal totalAmount) {}

    public record CashInStats(
            int days,
            List<CashInStatsBucket> buckets) {}
}
