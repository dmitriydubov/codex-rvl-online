package ru.projectx.contracts.service.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.repository.ContractRepository;

@Service
public class ContractPayments {

    private final ContractRepository contracts;
    private final Clock clock;

    public ContractPayments(ContractRepository contracts, Clock clock) {
        this.contracts = contracts;
        this.clock = clock;
    }

    @Transactional
    public void markPaid(UUID contractId, Instant paidAt) {
        Contract contract = contracts.findLockedById(contractId)
                .orElseThrow(() -> new IllegalStateException("договора КМ " + contractId + " нет"));
        contract.markPaid(paidAt, clock.instant());
    }
}
