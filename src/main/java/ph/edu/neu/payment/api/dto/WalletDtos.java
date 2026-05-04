package ph.edu.neu.payment.api.dto;

import ph.edu.neu.payment.domain.transaction.TransactionCategory;
import ph.edu.neu.payment.domain.wallet.WalletStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class WalletDtos {

    private WalletDtos() {}

    public record WalletView(
            UUID id,
            UUID userId,
            BigDecimal balance,
            String cardNumber,
            WalletStatus status,
            int validUntilYear) {}

    public record TransactionView(
            UUID id,
            String title,
            BigDecimal amount,
            TransactionCategory category,
            String reference,
            BigDecimal balanceAfter,
            OffsetDateTime occurredAt) {}

    public record TransactionPage(
            List<TransactionView> items,
            int page,
            int size,
            long totalElements) {}
}
