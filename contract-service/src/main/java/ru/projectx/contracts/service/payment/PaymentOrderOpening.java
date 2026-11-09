package ru.projectx.contracts.service.payment;

import java.time.Instant;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.OfferAcceptance;

public interface PaymentOrderOpening {

    void open(Contract contract, OfferAcceptance offer, Instant now);
}
