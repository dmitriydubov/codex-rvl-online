package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import ru.projectx.contracts.entity.RequestReference;
import ru.projectx.contracts.entity.TripPeriod;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.message.ContractTaskCancellation;
import ru.projectx.contracts.repository.ContractRepository;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.support.Tasks;

class TaskCancellationServiceTest {

    private static final Instant NOW = Instant.parse("2026-02-12T12:00:00Z");
    private static final Instant CANCELLED_AT = Instant.parse("2026-02-12T11:30:00Z");
    private static final RequestReference REQUEST =
            new RequestReference(Tasks.REQUEST, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY);
    private static final TripPeriod PERIOD = new TripPeriod(Tasks.FROM, Tasks.TO);

    private final ContractRepository contracts = mock();
    private final LmPolicyRepository policies = mock();
    private final TaskCancellationService service = new TaskCancellationService(contracts, policies);

    @Test
    void cancellation_stopsEveryContractAndPolicyOfTheRequestWithMappedReasonCommentAndMoment() {
        Contract contract = Contract.open(new ContractDraft("DKM-2026-000001", REQUEST, Tasks.SPOUSE, Tasks.INSURED,
                PERIOD, new BigDecimal("34000.00")), NOW);
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(REQUEST, Tasks.CHILD, Tasks.INSURED, PERIOD), NOW);
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
    void unknownReasonCode_isContractViolationBeforeAnyRecordIsTouched() {
        ContractTaskCancellation cancellation =
                new ContractTaskCancellation(Tasks.REQUEST, CANCELLED_AT, "SOMETHING_ELSE", null);

        assertThatThrownBy(() -> service.apply(cancellation, NOW))
                .isInstanceOf(ContractViolationException.class)
                .hasMessage("недопустимое значение поля data.cancel_reason");
        verifyNoInteractions(contracts, policies);
    }
}
