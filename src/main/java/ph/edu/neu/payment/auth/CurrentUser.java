package ph.edu.neu.payment.auth;

import org.springframework.security.core.context.SecurityContextHolder;

import ph.edu.neu.payment.common.error.UnauthorizedException;

public final class CurrentUser {

    private CurrentUser() {}

    public static AuthenticatedUser require() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser u)) {
            throw new UnauthorizedException("Authentication required");
        }
        return u;
    }

    public static AuthenticatedUser orNull() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser u)) return null;
        return u;
    }
}
