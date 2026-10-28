package ru.projectx.contracts.service.packages;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.LmPolicyStatus;

class PackageStatesTest {

    @ParameterizedTest
    @CsvSource({"NEW, NOT_REQUIRED", "DRAFT_READY, DRAFTED", "ACCEPTED, ACCEPTED", "INVOICE_ISSUED, INVOICE_ISSUED",
        "PAID, PAID", "SUBMITTED, PAID", "ISSUED, ISSUED", "ERROR, ERROR", "CANCELLED, CANCELLED"})
    void contractStatus_isShownAsTheStateOfThePackage(ContractStatus status, DocumentState state) {
        assertThat(PackageStates.of(status)).isEqualTo(state);
    }

    @ParameterizedTest
    @CsvSource({"NEW, NOT_REQUIRED", "SUBMITTED, NOT_REQUIRED", "ISSUED, ISSUED", "ERROR, ERROR",
        "CANCELLED, CANCELLED"})
    void policyStatus_isShownAsTheStateOfThePackage(LmPolicyStatus status, DocumentState state) {
        assertThat(PackageStates.of(status)).isEqualTo(state);
    }
}
