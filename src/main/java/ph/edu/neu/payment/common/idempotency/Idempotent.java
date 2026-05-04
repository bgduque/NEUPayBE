package ph.edu.neu.payment.common.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method that supports replay protection via the
 * {@code X-Idempotency-Key} header.
 *
 * If the header is absent, the method runs normally. If present, the first
 * call's serialized response body is stored against the key + caller user;
 * subsequent calls with the same key by the same user return the stored
 * response without re-executing the method.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
}
