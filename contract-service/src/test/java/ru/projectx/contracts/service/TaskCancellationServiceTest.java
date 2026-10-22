package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.CancelReason;
import ru.projectx.contracts.entity.Cancellation;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractDraft;
import ru.projectx.contracts.entity.ContractStatus;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyDraft;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.message.ContractTaskCancellation;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class TaskCancellationServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-12T12:00:00Z");
    private static final Instant CANCELLED_AT = Instant.parse("2026-02-12T11:30:00Z");

    private final ContractRepository contracts = mock();
    private final LmPolicyRepository policies = mock();
    private final CancellationUnknownReasons unknownReasons = mock();
    private final TaskCancellationService service = new TaskCancellationService(contracts, policies, unknownReasons);

    @Test
    void cancellation_stopsEveryContractAndPolicyOfTheRequestWithMappedReasonCommentAndMoment() {
        Contract contract = Contract.open(new ContractDraft("DKM-2026-000001", Issuances.TERMS, Tasks.SPOUSE,
                new BigDecimal("34000.00")), NOW);
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.CHILD), NOW);
        given(contracts.findByRequestIndividualRequestIdAndStatusNotIn(Tasks.REQUEST,
                Set.of(ContractStatus.ISSUED, ContractStatus.CANCELLED))).willReturn(List.of(contract));
        given(policies.findByRequestIndividualRequestIdAndStatusNotIn(Tasks.REQUEST,
                Set.of(LmPolicyStatus.ISSUED, LmPolicyStatus.CANCELLED))).willReturn(List.of(policy));

        service.apply(new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "HR_REJECTED", "Стенд: комментарий"),
                NOW);

        Cancellation expected = new Cancellation(CancelReason.REQUEST_CANCELLED, "Стенд: комментарий", CANCELLED_AT);
        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(expected);
        assertThat(contract.state().entityVersion()).isEqualTo(2);
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
        assertThat(policy.state().cancellation()).contains(expected);
    }

    @Test
    void knownReasonCode_doesNotAskForTheUnknownCodeRule() {
        service.apply(new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "EXPIRED", null), NOW);

        verifyNoInteractions(unknownReasons);
    }

    @Test
    void unknownReasonCode_cancelsEveryRecordWithTheOutcomeOfTheUnknownCodeRule() {
        Contract contract = Contract.open(new ContractDraft("DKM-2026-000001", Issuances.TERMS, Tasks.SPOUSE,
                new BigDecimal("34000.00")), NOW);
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(Issuances.TERMS, Tasks.CHILD), NOW);
        ContractTaskCancellation cancellation =
                new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "SOMETHING_ELSE", null);
        Cancellation asOther = new Cancellation(CancelReason.OTHER, "неизвестный код причины SOMETHING_ELSE",
                CANCELLED_AT);
        given(unknownReasons.asOther(cancellation)).willReturn(asOther);
        given(contracts.findByRequestIndividualRequestIdAndStatusNotIn(Tasks.REQUEST,
                Set.of(ContractStatus.ISSUED, ContractStatus.CANCELLED))).willReturn(List.of(contract));
        given(policies.findByRequestIndividualRequestIdAndStatusNotIn(Tasks.REQUEST,
                Set.of(LmPolicyStatus.ISSUED, LmPolicyStatus.CANCELLED))).willReturn(List.of(policy));

        service.apply(cancellation, NOW);

        assertThat(contract.status()).isEqualTo(ContractStatus.CANCELLED);
        assertThat(contract.state().cancellation()).contains(asOther);
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
        assertThat(policy.state().cancellation()).contains(asOther);
    }
}
