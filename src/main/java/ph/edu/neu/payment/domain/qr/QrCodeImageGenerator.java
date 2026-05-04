package ph.edu.neu.payment.domain.qr;

public interface QrCodeImageGenerator {
    byte[] toPng(String payload, int sizePx, int marginModules);
}
