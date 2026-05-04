package ph.edu.neu.payment.auth;

import ph.edu.neu.payment.domain.user.UserRole;

import java.util.UUID;

public interface JwtService {

    /** Issue an access JWT with role + optional step-up flag. */
    AccessTokenResult issueAccess(UUID userId, String email, UserRole role, boolean stepUp);

    /** Parse + verify, throwing on invalid/expired. */
    ParsedToken parse(String compactJwt);

    record AccessTokenResult(String token, java.time.OffsetDateTime expiresAt) {}

    record ParsedToken(UUID userId, String email, UserRole role, boolean stepUp,
                       java.time.OffsetDateTime expiresAt) {}
}
