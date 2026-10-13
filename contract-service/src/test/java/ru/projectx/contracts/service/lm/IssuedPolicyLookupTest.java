package ru.projectx.contracts.service.lm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.dto.ReferencePolicy;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.exception.ReferenceRejectedException;
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.exception.TemporaryFailureException;
import ru.projectx.contracts.service.reference.PolicyReads;
import ru.projectx.contracts.service.reference.ReferenceMetrics;

class IssuedPolicyLookupTest {

    private static final UUID EXTERNAL_ID = UUID.fromString("5f0b2a77-8c31-4f2e-9d44-7c1a0b93e512");
    private static final UUID OUR_ID = UUID.fromString("13ab5388-61ee-4e21-8a46-c325478b78c0");

    private final PolicyReads policies = mock();
    private final ReferenceMetrics metrics = mock();
    private final IssuedPolicyLookup lookup = new IssuedPolicyLookup(policies, metrics);

    @Test
    void foundPolicy_yieldsOurIdentifier() {
        given(policies.policyByExternalId(EXTERNAL_ID)).willReturn(new ReferencePolicy(OUR_ID, EXTERNAL_ID,
                "0555123456", LocalDate.parse("2026-04-10"), LocalDate.parse("2026-04-24"), false));

        assertThat(lookup.find(EXTERNAL_ID)).isEqualTo(OUR_ID);
        verifyNoInteractions(metrics);
    }

    @Test
    void policyNotReceivedYet_isTemporaryFailureNamingTheExternalIdWithoutCountingUnavailability() {
        given(policies.policyByExternalId(EXTERNAL_ID)).willThrow(new ReferenceUnavailableException(
                ReferenceUnavailableException.Reason.POLICY_NOT_RECEIVED));

        assertThatThrownBy(() -> lookup.find(EXTERNAL_ID)).isInstanceOf(TemporaryFailureException.class)
                .hasMessage("полис " + EXTERNAL_ID + " ещё не получен справочным сервисом");
        verifyNoInteractions(metrics);
    }

    @Test
    void unavailableReference_isTemporaryFailureAndCountsThePolicyKind() {
        given(policies.policyByExternalId(EXTERNAL_ID)).willThrow(new ReferenceUnavailableException(
                ReferenceUnavailableException.Reason.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> lookup.find(EXTERNAL_ID)).isInstanceOf(TemporaryFailureException.class)
                .hasMessage("справочный сервис недоступен");
        verify(metrics).countUnavailable(ReferenceKind.POLICY);
    }

    @Test
    void finalRefusal_propagates() {
        given(policies.policyByExternalId(EXTERNAL_ID)).willThrow(new ReferenceRejectedException(400, "VALIDATION_ERROR"));

        assertThatThrownBy(() -> lookup.find(EXTERNAL_ID)).isInstanceOf(ReferenceRejectedException.class);
    }
}
