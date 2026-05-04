package ph.edu.neu.payment.auth;

import ph.edu.neu.payment.api.dto.AuthDtos;
import ph.edu.neu.payment.domain.user.UserRole;

import java.util.UUID;

public interface AuthService {

    AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req);

    AuthDtos.AuthResponse login(AuthDtos.LoginRequest req, String deviceId);

    AuthDtos.AuthResponse refresh(String refreshToken, String deviceId);

    void logout(String refreshToken);

    /**
     * Issue a step-up access token after successful biometric verification.
     * The token is short-lived and required for sensitive endpoints (top-up, payment).
     */
    AuthDtos.StepUpResponse issueStepUp(UUID userId, String email, UserRole role);

    /**
     * Web alternative to biometric step-up: re-confirm the user's password and
     * mint a short-lived step-up JWT. Throws UnauthorizedException on bad password.
     */
    AuthDtos.StepUpResponse passwordStepUp(UUID userId, String password);
}
