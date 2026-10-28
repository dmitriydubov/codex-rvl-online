package ru.projectx.contracts.service;

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
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.entity.issuance.TripPeriod;
import ru.projectx.contracts.message.task.ContractTask;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class KmContractAcceptedIssuanceTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");

    private final ContractRepository contracts = mock();
    private final ContractNumbers numbers = mock();
    private final PartySnapshotStore parties = mock();
    private final KmContractIssuance issuance = new KmContractIssuance(contracts, numbers, parties);
    private final ContractTask task = Tasks.task(Tasks.insured("0.00"), Tasks.spouse("34000.00"));
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
    void acceptedContract_withChangedPremium_keepsItsDataAndWarnsWithIdentifiersAndTaskVersion() {
        Contract accepted = accepted();
        given(parties.differ(eq(PersonOwner.contract(accepted.id())), anyList())).willReturn(false);
        ContractTask repriced = new ContractTask(2, Tasks.REQUEST, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY,
                Tasks.FROM, Tasks.TO, List.of(Tasks.insured("0.00"), Tasks.spouse("36000.00")));

        issuance.reconcile(new Issue(repriced, Tasks.spouse("36000.00")), Optional.of(accepted), NOW);

        assertThat(accepted.status()).isEqualTo(ContractStatus.ACCEPTED);
        assertThat(accepted.premiumAmount()).isEqualByComparingTo("34000.00");
        assertThat(accepted.state().entityVersion()).isEqualTo(1);
        verify(parties, never()).replaceIfChanged(any(), anyList(), any());
        verify(contracts, never()).save(any());
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("договор КМ " + accepted.id() + " участника " + Tasks.SPOUSE + " заявки " + Tasks.REQUEST
                        + ": договор КМ акцептован, изменения из задачи версии 2 не применены");
    }

    @Test
    void acceptedContract_withChangedDates_keepsTheTripPeriodAndWarns() {
        Contract accepted = accepted();
        given(parties.differ(eq(PersonOwner.contract(accepted.id())), anyList())).willReturn(false);
        ContractTask moved = Tasks.task(Tasks.FROM.plusDays(1), Tasks.TO.plusDays(1),
                Tasks.insured("0.00"), Tasks.spouse("34000.00"));

        issuance.reconcile(new Issue(moved, Tasks.spouse("34000.00")), Optional.of(accepted), NOW);

        assertThat(accepted.state().period()).isEqualTo(new TripPeriod(Tasks.FROM, Tasks.TO));
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN).hasSize(1);
    }

    @Test
    void acceptedContract_withChangedPartyOnly_warns() {
        Contract accepted = accepted();
        given(parties.differ(eq(PersonOwner.contract(accepted.id())), anyList())).willReturn(true);

        issuance.reconcile(new Issue(task, Tasks.spouse("34000.00")), Optional.of(accepted), NOW);

        assertThat(accepted.state().entityVersion()).isEqualTo(1);
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN).hasSize(1);
    }

    @Test
    void acceptedContract_withInvalidParticipantData_staysAcceptedWithoutErrorAndWarns() {
        Contract accepted = accepted();
        ContractTask invalid = Tasks.task(Tasks.insured("0.00"), Tasks.relative(1, Tasks.SPOUSE, "34000.00", null));

        issuance.reconcile(new Issue(invalid, invalid.participants().get(1)), Optional.of(accepted), NOW);

        assertThat(accepted.status()).isEqualTo(ContractStatus.ACCEPTED);
        assertThat(accepted.state().error()).isEmpty();
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN).hasSize(1);
    }

    @Test
    void acceptedContract_withUnchangedTask_staysSilent() {
        Contract accepted = accepted();
        given(parties.differ(eq(PersonOwner.contract(accepted.id())), anyList())).willReturn(false);

        issuance.reconcile(new Issue(task, Tasks.spouse("34000.00")), Optional.of(accepted), NOW);

        assertThat(accepted.state().entityVersion()).isEqualTo(1);
        assertThat(journal.list).isEmpty();
    }

    @Test
    void contractReadyForAcceptance_isNotFrozen() {
        Contract ready = existing("34000.00");
        ready.offerDraft();

        assertThat(issuance.frozenBecause(ready)).isEmpty();
    }

    private static Contract existing(String premium) {
        return Contract.open(new ContractDraft("DKM-2026-000001", Issuances.TERMS, Tasks.SPOUSE,
                new BigDecimal(premium)), NOW);
    }

    private static Contract accepted() {
        Contract contract = existing("34000.00");
        contract.offerDraft();
        contract.accept();
        return contract;
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(RecordIssuance.class);
    }
}
