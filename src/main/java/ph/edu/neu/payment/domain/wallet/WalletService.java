package ph.edu.neu.payment.domain.wallet;

import ph.edu.neu.payment.api.dto.WalletDtos;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletService {

    WalletDtos.WalletView getMyWallet(UUID userId);

    WalletDtos.WalletView getWalletForUser(UUID userId);

    /** Apply a signed delta to a wallet. Caller must have already authorized the operation. */
    BigDecimal applyDelta(UUID walletId, BigDecimal delta);

    void freeze(UUID walletId);
    void activate(UUID walletId);
}
