package ru.projectx.contracts.service.lm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;

class LmPolicyKindTest {

    @Test
    void policyKind_requestsAndAwaitsThePolicyByItsIssuanceFlow() {
        assertThat(LmPolicyKind.KIND.flow()).isSameAs(LmPolicyStatus.ISSUANCE_FLOW);
    }
}
