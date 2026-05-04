package ph.edu.neu.payment.domain.cashin;

import ph.edu.neu.payment.api.dto.CashInDtos;

import java.util.List;

public interface CashInLocationService {
    List<CashInDtos.LocationView> list();
}
