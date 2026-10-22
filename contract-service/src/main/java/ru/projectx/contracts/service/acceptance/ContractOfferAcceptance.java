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
import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractAcceptance;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.exception.ContractAlreadyAcceptedException;
import ru.projectx.contracts.exception.ContractBusyException;
import ru.projectx.contracts.exception.ContractNotFoundException;
import ru.projectx.contracts.exception.ContractStateException;
import ru.projectx.contracts.exception.DocumentVersionMismatchException;
import ru.projectx.contracts.repository.ContractAcceptanceRepository;
import ru.projectx.contracts.repository.ContractRepository;

@Service
public class ContractOfferAcceptance implements OfferAcceptances {

    private static final Logger log = LoggerFactory.getLogger(ContractOfferAcceptance.class);

    private final ContractRepository contracts;
    private final ContractAcceptanceRepository acceptances;
    private final Clock clock;

    public ContractOfferAcceptance(ContractRepository contracts, ContractAcceptanceRepository acceptances,
            Clock clock) {
        this.contracts = contracts;
        this.acceptances = acceptances;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AcceptanceResponse accept(UUID contractId, AcceptanceRequest request) {
        Contract contract = locked(contractId);
        ensureAcceptable(contract);
        ContractAcceptance acceptance = acceptances.findByContractId(contractId)
                .map(previous -> repeated(previous, request))
                .orElseGet(() -> first(contract, request));
        return new AcceptanceResponse(contractId, DocumentState.ACCEPTED, acceptance.offer().acceptedAt());
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
        if (contract.status() != ContractStatus.DRAFT_READY && !contract.offerAccepted()) {
            throw new ContractStateException(contract.id(), contract.status().name());
        }
    }

    private static ContractAcceptance repeated(ContractAcceptance previous, AcceptanceRequest request) {
        int acceptedVersion = previous.offer().contractVersion();
        if (acceptedVersion != request.contractVersion()) {
            throw new ContractAlreadyAcceptedException(previous.contractId(), acceptedVersion);
        }
        return previous;
    }

    private ContractAcceptance first(Contract contract, AcceptanceRequest request) {
        int currentVersion = contract.state().entityVersion();
        if (currentVersion != request.contractVersion()) {
            throw new DocumentVersionMismatchException(contract.id(), currentVersion);
        }
        Instant now = clock.instant();
        ContractAcceptance acceptance =
                acceptances.save(ContractAcceptance.record(contract.id(), AcceptanceForms.offer(request), now));
        contract.accept();
        log.info("договор КМ {} акцептован по версии {}, канал {}", contract.id(), currentVersion,
                request.channel());
        return acceptance;
    }
}
