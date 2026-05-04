package ph.edu.neu.payment.common.error;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiError(
        String code,
        String message,
        OffsetDateTime timestamp,
        List<FieldViolation> violations) {

    public static ApiError of(String code, String message) {
        return new ApiError(code, message, OffsetDateTime.now(), List.of());
    }

    public static ApiError of(String code, String message, List<FieldViolation> violations) {
        return new ApiError(code, message, OffsetDateTime.now(), violations);
    }

    public record FieldViolation(String field, String message) {}
}
