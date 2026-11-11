package ru.projectx.contracts.service.packages;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import ru.projectx.contracts.dto.DocumentState;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.service.status.PaymentOffer;

class PackageStatesTest {

    @ParameterizedTest
    @CsvSource({"NEW, NOT_REQUIRED", "DRAFT_READY, DRAFTED", "ACCEPTED, ACCEPTED", "INVOICE_ISSUED, INVOICE_ISSUED",
        "PAID, PAID", "SUBMITTED, PAID", "ISSUED, ISSUED", "ERROR, ERROR", "CANCELLED, CANCELLED"})
    void contractStatus_isShownAsTheStateOfThePackage(ContractStatus status, DocumentState state) {
        assertThat(PackageStates.of(status)).isEqualTo(state);
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"ACCEPTED", "INVOICE_ISSUED"})
    void contractWithDeferredCancellation_isShownAsCancelled(ContractStatus status) {
        assertThat(PackageStates.shown(status, new PaymentOffer(true, Optional.empty(), Optional.empty())))
                .isEqualTo(DocumentState.CANCELLED);
    }

    @ParameterizedTest
    @CsvSource({"ACCEPTED, ACCEPTED", "INVOICE_ISSUED, INVOICE_ISSUED", "PAID, PAID", "CANCELLED, CANCELLED"})
    void contractWithoutDeferredCancellation_isShownByItsOwnStatus(ContractStatus status, DocumentState state) {
        PaymentOffer linked = new PaymentOffer(false, Optional.of("https://pay.example.test/pay/7c1e5a90"),
                Optional.of(Instant.parse("2026-02-13T07:05:00Z")));

        assertThat(PackageStates.shown(status, linked)).isEqualTo(state);
        assertThat(PackageStates.shown(status, new PaymentOffer(false, Optional.empty(), Optional.empty())))
                .isEqualTo(state);
    }

    @ParameterizedTest
    @CsvSource({"NEW, NOT_REQUIRED", "SUBMITTED, NOT_REQUIRED", "ISSUED, ISSUED", "ERROR, ERROR",
        "CANCELLED, CANCELLED"})
    void policyStatus_isShownAsTheStateOfThePackage(LmPolicyStatus status, DocumentState state) {
        assertThat(PackageStates.of(status)).isEqualTo(state);
    }
}
