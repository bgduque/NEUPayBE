package ph.edu.neu.payment.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ph.edu.neu.payment.api.dto.AuthDtos;
import ph.edu.neu.payment.auth.AuthService;
import ph.edu.neu.payment.auth.CurrentUser;
import ph.edu.neu.payment.auth.JwtAuthenticationFilter;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public AuthDtos.AuthResponse register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        return auth.register(req);
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest req,
                                       @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        return auth.login(req, deviceId);
    }

    @PostMapping("/refresh")
    public AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest req,
                                         @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        return auth.refresh(req.refreshToken(), deviceId);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody AuthDtos.RefreshRequest req,
                                       @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
        // Authorization header check is handled by the security filter; this only
        // revokes the refresh token. Access tokens are short-lived.
        if (authHeader == null || !authHeader.startsWith(JwtAuthenticationFilter.BEARER_PREFIX)) {
            return ResponseEntity.status(401).build();
        }
        auth.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Password-based step-up for the web cashier dashboard. The iOS app uses
     * Face ID via {@code /api/v1/biometric/verify}; the browser cannot reach
     * the Secure Enclave so it re-confirms the cashier's password instead.
     */
    @PostMapping("/step-up/password")
    public AuthDtos.StepUpResponse stepUpPassword(@Valid @RequestBody AuthDtos.PasswordStepUpRequest req) {
        return auth.passwordStepUp(CurrentUser.require().id(), req.password());
    }
}
