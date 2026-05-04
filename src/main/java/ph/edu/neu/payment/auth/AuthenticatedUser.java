package ph.edu.neu.payment.auth;

import ph.edu.neu.payment.domain.user.UserRole;

import java.util.UUID;

/** Lightweight principal stored in the SecurityContext after JWT parsing. */
public record AuthenticatedUser(UUID id, String email, UserRole role, boolean stepUp) {
}
