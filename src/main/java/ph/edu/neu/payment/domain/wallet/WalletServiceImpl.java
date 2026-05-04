package ph.edu.neu.payment.domain.wallet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ph.edu.neu.payment.api.dto.WalletDtos;
import ph.edu.neu.payment.common.error.NotFoundException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class WalletServiceImpl implements WalletService {

    private final WalletRepository wallets;

    public WalletServiceImpl(WalletRepository wallets) {
        this.wallets = wallets;
    }

    @Override
    @Transactional(readOnly = true)
    public WalletDtos.WalletView getMyWallet(UUID userId) {
        return getWalletForUser(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletDtos.WalletView getWalletForUser(UUID userId) {
        Wallet w = wallets.findByUser_Id(userId)
                .orElseThrow(() -> new NotFoundException("Wallet not found for user"));
        return view(w);
    }

    @Override
    @Transactional
    public BigDecimal applyDelta(UUID walletId, BigDecimal delta) {
        Wallet w = wallets.findWithLockingById(walletId)
                .orElseThrow(() -> new NotFoundException("Wallet not found"));
        return w.applyDelta(delta);
    }

    @Override
    @Transactional
    public void freeze(UUID walletId) {
        wallets.findById(walletId).orElseThrow(() -> new NotFoundException("Wallet not found")).freeze();
    }

    @Override
    @Transactional
    public void activate(UUID walletId) {
        wallets.findById(walletId).orElseThrow(() -> new NotFoundException("Wallet not found")).activate();
    }

    static WalletDtos.WalletView view(Wallet w) {
        return new WalletDtos.WalletView(
                w.getId(),
                w.getUser().getId(),
                w.getBalance(),
                w.getCardNumber(),
                w.getStatus(),
                w.getValidUntilYear());
    }
}
