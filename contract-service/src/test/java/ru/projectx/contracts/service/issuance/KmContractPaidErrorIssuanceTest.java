package ru.projectx.contracts.service.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractDraft;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.message.task.ContractTask;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class KmContractPaidErrorIssuanceTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:35:00Z");
    private static final Instant PAID_AT = Instant.parse("2026-02-10T07:20:15Z");
    private static final IssueError UNDELIVERED =
            new IssueError("KM_CONTRACT_SUBMISSION_UNDELIVERED", "договор КМ не передан в 1С:УСК");

    private final ContractRepository contracts = mock();
    private final ContractNumbers numbers = mock();
    private final PartySnapshotStore parties = mock();
    private final KmContractIssuance issuance = new KmContractIssuance(contracts, numbers, parties);
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();

    @BeforeEach
    void startJournal() {
        journal.start();
        logger().addAppender(journal);
    }

    @AfterEach
    void stopJournal() {
        logger().detachAppender(journal);
        journal.stop();
    }

    @Test
    void paidContractInError_withChangedPremium_keepsItsErrorAndPremiumAndWarnsThatItIsPaid() {
        Contract failed = paidAndFailed();
        given(parties.differ(eq(PersonOwner.contract(failed.id())), anyList())).willReturn(false);
        ContractTask repriced = new ContractTask(2, Tasks.REQUEST, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY,
                Tasks.FROM, Tasks.TO, List.of(Tasks.insured("0.00"), Tasks.spouse("36000.00")));

        issuance.reconcile(new Issue(repriced, Tasks.spouse("36000.00")), Optional.of(failed), NOW);

        assertThat(failed.status()).isEqualTo(ContractStatus.ERROR);
        assertThat(failed.state().error()).contains(UNDELIVERED);
        assertThat(failed.premiumAmount()).isEqualByComparingTo("34000.00");
        assertThat(failed.state().entityVersion()).isEqualTo(3);
        verify(parties, never()).replaceIfChanged(any(), anyList(), any());
        verify(contracts, never()).save(any());
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("договор КМ " + failed.id() + " участника " + Tasks.SPOUSE + " заявки " + Tasks.REQUEST
                        + ": договор КМ оплачен, изменения из задачи версии 2 не применены");
    }

    @Test
    void paidContractInError_withInvalidParticipantData_keepsItsErrorCodeAndWarns() {
        Contract failed = paidAndFailed();
        ContractTask invalid = Tasks.task(Tasks.insured("0.00"), Tasks.relative(1, Tasks.SPOUSE, "34000.00", null));

        issuance.reconcile(new Issue(invalid, invalid.participants().get(1)), Optional.of(failed), NOW);

        assertThat(failed.state().error()).contains(UNDELIVERED);
        assertThat(failed.state().entityVersion()).isEqualTo(3);
        verify(contracts, never()).save(any());
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN).hasSize(1);
    }

    @Test
    void paidContractInError_withUnchangedTask_staysSilent() {
        Contract failed = paidAndFailed();
        given(parties.differ(eq(PersonOwner.contract(failed.id())), anyList())).willReturn(false);
        ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"));

        issuance.reconcile(new Issue(task, Tasks.spouse("34000.00")), Optional.of(failed), NOW);

        assertThat(failed.state().entityVersion()).isEqualTo(3);
        verify(contracts, never()).save(any());
        assertThat(journal.list).isEmpty();
    }

    private static Contract paidAndFailed() {
        Contract contract = Contract.open(new ContractDraft("DKM-2026-000001", Issuances.TERMS, Tasks.SPOUSE,
                new BigDecimal("34000.00")), NOW);
        contract.offerDraft();
        contract.accept();
        contract.markPaid(PAID_AT, NOW);
        contract.markError(UNDELIVERED, NOW);
        return contract;
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(RecordIssuance.class);
    }
}
