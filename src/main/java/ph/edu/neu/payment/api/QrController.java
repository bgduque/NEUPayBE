package ph.edu.neu.payment.api;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import ph.edu.neu.payment.api.dto.QrDtos;
import ph.edu.neu.payment.auth.CurrentUser;
import ph.edu.neu.payment.domain.qr.QrMode;
import ph.edu.neu.payment.domain.qr.QrService;

@RestController
@RequestMapping("/api/v1/qr")
public class QrController {

    private final QrService qrService;

    public QrController(QrService qrService) {
        this.qrService = qrService;
    }

    @PostMapping("/issue")
    public QrDtos.IssueResponse issue(@Valid @RequestBody QrDtos.IssueRequest req) {
        return qrService.issue(CurrentUser.require().id(), req.mode());
    }

    @GetMapping(value = "/me/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> myImage(@RequestParam QrMode mode) {
        byte[] png = qrService.renderPng(CurrentUser.require().id(), mode);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(png);
    }

    @PostMapping("/redeem")
    @PreAuthorize("hasAnyRole('CASHIER','ADMIN')")
    public QrDtos.RedeemResponse redeem(@Valid @RequestBody QrDtos.RedeemRequest req) {
        return qrService.redeem(req.token(), CurrentUser.require().id());
    }
}
