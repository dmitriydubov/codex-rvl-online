package ru.projectx.contracts.service.km;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import ru.projectx.contracts.config.KmContractSubmissionProperties;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;
import ru.projectx.contracts.support.TestLeadership;

class KmContractAwaitingTest {

    private static final Instant NOW = Instant.parse("2026-02-10T08:35:00Z");
    private static final Duration THRESHOLD = Duration.ofHours(2);

    private final ContractRepository contracts = mock();
    private final KmContractAwaitingMetrics metrics = mock();
    private final PlatformTransactionManager transactions = mock();
    private final KmContractAwaiting awaiting = new KmContractAwaiting(contracts, metrics,
            new KmContractSubmissionProperties(Duration.ofSeconds(10), 100,
                    Duration.ofMinutes(1), THRESHOLD),
            Clock.fixed(NOW, ZoneOffset.UTC), TestLeadership.leader(), transactions);

    @BeforeEach
    void transactionsOpen() {
        given(transactions.getTransaction(any())).willReturn(new SimpleTransactionStatus());
    }

    @Test
    void submittedContracts_showTheWaitOfTheOldestAndTheCountSubmittedBeforeTheThreshold() {
        Contract oldest = Issuances.paidContract("0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64", Tasks.SPOUSE, "DKM-2026-000001",
                NOW.minus(Duration.ofMinutes(160)));
        oldest.submit(NOW.minus(Duration.ofMinutes(150)));
        given(contracts.findFirstByStatusOrderByUpdatedAtAscIdAsc(ContractStatus.SUBMITTED)).willReturn(Optional.of(oldest));
        given(contracts.countByStatusAndUpdatedAtBefore(ContractStatus.SUBMITTED, NOW.minus(THRESHOLD))).willReturn(3L);

        awaiting.refresh();

        verify(metrics).show(Duration.ofMinutes(150), 3);
    }

    @Test
    void noSubmittedContracts_showZeroWaitAndNothingOverdue() {
        given(contracts.findFirstByStatusOrderByUpdatedAtAscIdAsc(ContractStatus.SUBMITTED)).willReturn(Optional.empty());
        given(contracts.countByStatusAndUpdatedAtBefore(ContractStatus.SUBMITTED, NOW.minus(THRESHOLD))).willReturn(0L);

        awaiting.refresh();

        verify(metrics).show(Duration.ZERO, 0);
    }
}
