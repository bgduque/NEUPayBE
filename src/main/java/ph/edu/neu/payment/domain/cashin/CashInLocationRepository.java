package ph.edu.neu.payment.domain.cashin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CashInLocationRepository extends JpaRepository<CashInLocation, UUID> {
    List<CashInLocation> findByActiveTrueOrderByName();
}
