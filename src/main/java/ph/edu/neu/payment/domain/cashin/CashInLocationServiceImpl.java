package ph.edu.neu.payment.domain.cashin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ph.edu.neu.payment.api.dto.CashInDtos;

import java.util.List;

@Service
public class CashInLocationServiceImpl implements CashInLocationService {

    private final CashInLocationRepository repo;

    public CashInLocationServiceImpl(CashInLocationRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashInDtos.LocationView> list() {
        return repo.findByActiveTrueOrderByName().stream()
                .map(l -> new CashInDtos.LocationView(l.getId(), l.getName(), l.getSublabel(), l.getKind()))
                .toList();
    }
}
