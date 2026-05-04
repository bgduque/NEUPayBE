package ph.edu.neu.payment.common.error;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
