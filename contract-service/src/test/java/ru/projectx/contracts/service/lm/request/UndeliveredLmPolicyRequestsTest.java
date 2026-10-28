package ru.projectx.contracts.service.lm.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.CannotAcquireLockException;
import ru.projectx.contracts.entity.issuance.CancelReason;
import ru.projectx.contracts.entity.issuance.Cancellation;
import ru.projectx.contracts.entity.issuance.IssueError;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.repository.issuance.LmPolicyRepository;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

class UndeliveredLmPolicyRequestsTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:20:00Z");
    private static final UUID POLICY_ID = UUID.fromString("8e2d0b17-5a44-4c90-b2e3-71f0c6a45d39");
    private static final IssuedPolicy ISSUED = new IssuedPolicy(UUID.fromString("cb904f2a-7d61-4e08-89a5-40b1e3c7f012"),
            "LM-000012204", UUID.fromString("13ab5388-61ee-4e21-8a46-c325478b78c0"), "0555123456");

    private final LmPolicyRepository policies = mock();
    private final RequestStatusPublication statuses = mock();
    private final IssuanceSnapshot before = mock();
    private final UndeliveredLmPolicyRequests undelivered = new UndeliveredLmPolicyRequests(policies, statuses);
    private final LmPolicy policy = Issuances.policy(POLICY_ID.toString(), Tasks.CHILD);

    @BeforeEach
    void policyIsStored() {
        given(policies.findLockedById(POLICY_ID)).willReturn(Optional.of(policy));
        given(statuses.watch(Tasks.REQUEST)).willReturn(before);
    }

    @Test
    void reactsToRejectedRequestsOfLmPolicies() {
        assertThat(undelivered.aggregateType()).isEqualTo("LM_POLICY");
    }

    @Test
    void submittedPolicy_goesToErrorWithTheUndeliveredCode_andTheRequestServiceIsAnswered() {
        policy.submit(Issuances.OPENED);

        undelivered.rejected(POLICY_ID, NOW);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ERROR);
        assertThat(policy.state().error()).contains(new IssueError("LM_POLICY_REQUEST_UNDELIVERED",
                "запрос на выпуск полиса ЛМ не доставлен в 1С:УСК"));
        assertThat(policy.state().entityVersion()).isEqualTo(3);
        InOrder order = inOrder(statuses);
        order.verify(statuses).watch(Tasks.REQUEST);
        order.verify(statuses).afterRecordChange(before, NOW);
    }

    @Test
    void policyNoLongerSubmitted_staysAsItIs() {
        policy.submit(Issuances.OPENED);
        Cancellation cancellation = new Cancellation(CancelReason.INSURED_REFUSED, null, NOW);
        policy.cancel(cancellation, NOW);

        undelivered.rejected(POLICY_ID, NOW);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.CANCELLED);
        assertThat(policy.state().error()).isEmpty();
        assertThat(policy.state().entityVersion()).isEqualTo(3);
        verifyNoInteractions(statuses);
    }

    @Test
    void newPolicy_isNotTakenForSubmitted() {
        undelivered.rejected(POLICY_ID, NOW);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.NEW);
        verifyNoInteractions(statuses);
    }

    @Test
    void issuedPolicy_keepsTheIssueWithoutErrorOrAnswer() {
        policy.submit(Issuances.OPENED);
        policy.markIssued(ISSUED, NOW);
        int issuedVersion = policy.state().entityVersion();

        undelivered.rejected(POLICY_ID, NOW);

        assertThat(policy.status()).isEqualTo(LmPolicyStatus.ISSUED);
        assertThat(policy.issued()).contains(ISSUED);
        assertThat(policy.state().error()).isEmpty();
        assertThat(policy.state().entityVersion()).isEqualTo(issuedVersion);
        verifyNoInteractions(statuses);
    }

    @Test
    void policyRowHeldLongerThanTheLockTimeout_failsTheReactionBeforeAnyDecision() {
        CannotAcquireLockException busy = new CannotAcquireLockException("строка полиса занята");
        given(policies.findLockedById(POLICY_ID)).willThrow(busy);

        assertThatThrownBy(() -> undelivered.rejected(POLICY_ID, NOW)).isSameAs(busy);
        assertThat(policy.status()).isEqualTo(LmPolicyStatus.NEW);
        verifyNoInteractions(statuses);
        verify(policies, never()).findById(any());
    }

    @Test
    void unknownPolicy_isIllegalState() {
        UUID unknown = UUID.fromString("00000000-0000-4000-8000-000000000001");
        given(policies.findLockedById(unknown)).willReturn(Optional.empty());

        assertThatThrownBy(() -> undelivered.rejected(unknown, NOW)).isInstanceOf(IllegalStateException.class)
                .hasMessage("полиса ЛМ " + unknown + " нет");
        verifyNoInteractions(statuses);
    }
}
