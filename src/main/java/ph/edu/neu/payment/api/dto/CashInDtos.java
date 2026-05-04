package ph.edu.neu.payment.api.dto;

import ph.edu.neu.payment.domain.cashin.CashInKind;

import java.util.UUID;

public final class CashInDtos {

    private CashInDtos() {}

    public record LocationView(UUID id, String name, String sublabel, CashInKind kind) {}
}
