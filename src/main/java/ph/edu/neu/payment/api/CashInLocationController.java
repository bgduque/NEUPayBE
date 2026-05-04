package ph.edu.neu.payment.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ph.edu.neu.payment.api.dto.CashInDtos;
import ph.edu.neu.payment.domain.cashin.CashInLocationService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cash-in-locations")
public class CashInLocationController {

    private final CashInLocationService locations;

    public CashInLocationController(CashInLocationService locations) {
        this.locations = locations;
    }

    @GetMapping
    public List<CashInDtos.LocationView> list() {
        return locations.list();
    }
}
