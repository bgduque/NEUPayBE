package ph.edu.neu.payment.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ph.edu.neu.payment.auth.AuthenticatedUser;
import ph.edu.neu.payment.auth.CurrentUser;
import ph.edu.neu.payment.common.error.ConflictException;

import java.lang.reflect.Method;
import java.util.Optional;

@Aspect
@Component
public class IdempotencyAspect {

    public static final String HEADER = "X-Idempotency-Key";
    private static final int MAX_KEY_LEN = 120;

    private final IdempotencyKeyRepository keys;
    private final ObjectMapper mapper;

    public IdempotencyAspect(IdempotencyKeyRepository keys, ObjectMapper mapper) {
        this.keys = keys;
        this.mapper = mapper;
    }

    @Around("@annotation(ph.edu.neu.payment.common.idempotency.Idempotent)")
    @Transactional
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return pjp.proceed();
        }
        HttpServletRequest req = sra.getRequest();
        String key = req.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            return pjp.proceed();
        }
        if (key.length() > MAX_KEY_LEN) {
            throw new ConflictException("Idempotency key too long");
        }

        AuthenticatedUser user = CurrentUser.require();

        Optional<IdempotencyKey> existing = keys.findByKey(key);
        if (existing.isPresent()) {
            IdempotencyKey row = existing.get();
            if (!row.getUserId().equals(user.id())
                    || !row.getMethod().equals(req.getMethod())
                    || !row.getPath().equals(req.getRequestURI())) {
                throw new ConflictException("Idempotency key reused with different request");
            }
            return deserialize(row.getResponseBody(), returnType(pjp));
        }

        Object result = pjp.proceed();
        keys.save(new IdempotencyKey(
                key, user.id(), req.getMethod(), req.getRequestURI(),
                serialize(result), 200));
        return result;
    }

    private static Class<?> returnType(ProceedingJoinPoint pjp) {
        Method m = ((MethodSignature) pjp.getSignature()).getMethod();
        return m.getReturnType();
    }

    private String serialize(Object o) {
        if (o == null) return null;
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to cache idempotent response", ex);
        }
    }

    private Object deserialize(String json, Class<?> type) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read cached idempotent response", ex);
        }
    }

}
