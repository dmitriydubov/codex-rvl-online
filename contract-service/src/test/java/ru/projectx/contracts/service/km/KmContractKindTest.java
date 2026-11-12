package ru.projectx.contracts.service.km;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.contract.ContractStatus;

class KmContractKindTest {

    @Test
    void contractKind_sendsAndAwaitsTheContractByItsIssuanceFlow() {
        assertThat(KmContractKind.KIND.flow()).isSameAs(ContractStatus.ISSUANCE_FLOW);
    }
}
