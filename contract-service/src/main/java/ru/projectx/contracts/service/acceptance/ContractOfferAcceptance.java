package ru.projectx.contracts.service.acceptance;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.dto.AcceptanceRequest;
import ru.projectx.contracts.dto.AcceptanceResponse;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractAcceptance;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.contract.ContractStep;
import ru.projectx.contracts.exception.ContractBusyException;
import ru.projectx.contracts.exception.ContractNotFoundException;
import ru.projectx.contracts.exception.ContractStateException;
import ru.projectx.contracts.repository.issuance.ContractAcceptanceRepository;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.service.payment.OrderOpening;
import ru.projectx.contracts.service.payment.PaymentOrderOpening;

@Service
public class ContractOfferAcceptance implements OfferAcceptances {

    private static final Logger log = LoggerFactory.getLogger(ContractOfferAcceptance.class);

    private final ContractRepository contracts;
    private final ContractAcceptanceRepository acceptances;
    private final PaymentOrderOpening paymentOrders;
    private final Clock clock;

    public ContractOfferAcceptance(ContractRepository contracts, ContractAcceptanceRepository acceptances,
            PaymentOrderOpening paymentOrders, Clock clock) {
        this.contracts = contracts;
        this.acceptances = acceptances;
        this.paymentOrders = paymentOrders;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AcceptanceResponse accept(UUID contractId, AcceptanceRequest request) {
        OrderOpening paymentOrder = paymentOrders.forPayer(request.email());
        Contract contract = locked(contractId);
        ensureAcceptable(contract);
        ContractAcceptance acceptance = acceptances.findByContractId(contractId)
                .map(previous -> repeated(previous, request))
                .orElseGet(() -> first(contract, request, paymentOrder));
        return AcceptanceResponse.accepted(contractId, acceptance.offer().acceptedAt());
    }

    private Contract locked(UUID contractId) {
        try {
            return contracts.findLockedById(contractId).orElseThrow(() -> new ContractNotFoundException(contractId));
        }
        catch (PessimisticLockingFailureException busy) {
            throw new ContractBusyException(contractId, busy);
        }
    }

    private static void ensureAcceptable(Contract contract) {
        if (contract.status() != ContractStatus.DRAFT_READY && !contract.status().offerAccepted()) {
            throw new ContractStateException(contract.id(), contract.status().name());
        }
    }

    private static ContractAcceptance repeated(ContractAcceptance previous, AcceptanceRequest request) {
        OfferVersions.ensureAccepted(previous, request.contractVersion());
        return previous;
    }

    private ContractAcceptance first(Contract contract, AcceptanceRequest request, OrderOpening paymentOrder) {
        int currentVersion = OfferVersions.current(contract, request.contractVersion());
        Instant now = clock.instant();
        ContractAcceptance acceptance =
                acceptances.save(ContractAcceptance.record(contract.id(), AcceptanceForms.offer(request), now));
        contract.advance(ContractStep.OFFER_ACCEPTED);
        paymentOrder.open(contract, acceptance.offer(), now);
        log.info("договор КМ {} акцептован по версии {}, канал {}", contract.id(), currentVersion,
                request.channel());
        return acceptance;
    }
}
