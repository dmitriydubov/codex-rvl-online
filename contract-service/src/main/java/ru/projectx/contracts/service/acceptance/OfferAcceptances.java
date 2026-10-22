package ru.projectx.contracts.service.acceptance;

import java.util.UUID;
import ru.projectx.contracts.dto.AcceptanceRequest;
import ru.projectx.contracts.dto.AcceptanceResponse;

public interface OfferAcceptances {

    AcceptanceResponse accept(UUID contractId, AcceptanceRequest request);
}
