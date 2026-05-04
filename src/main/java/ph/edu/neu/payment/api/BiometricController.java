package ph.edu.neu.payment.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import ph.edu.neu.payment.api.dto.BiometricDtos;
import ph.edu.neu.payment.auth.CurrentUser;
import ph.edu.neu.payment.domain.biometric.BiometricService;

@RestController
@RequestMapping("/api/v1/biometric")
public class BiometricController {

    private final BiometricService biometric;

    public BiometricController(BiometricService biometric) {
        this.biometric = biometric;
    }

    @PostMapping("/enroll")
    public BiometricDtos.EnrollResponse enroll(@Valid @RequestBody BiometricDtos.EnrollRequest req) {
        return biometric.enroll(CurrentUser.require().id(), req);
    }

    @PostMapping("/challenge")
    public BiometricDtos.ChallengeResponse challenge(@Valid @RequestBody BiometricDtos.ChallengeRequest req) {
        return biometric.issueChallenge(CurrentUser.require().id(), req);
    }

    @PostMapping("/verify")
    public BiometricDtos.VerifyResponse verify(@Valid @RequestBody BiometricDtos.VerifyRequest req) {
        return biometric.verify(CurrentUser.require().id(), req);
    }

    @DeleteMapping("/credentials/{deviceId}")
    public void revoke(@PathVariable String deviceId) {
        biometric.revoke(CurrentUser.require().id(), deviceId);
    }
}
