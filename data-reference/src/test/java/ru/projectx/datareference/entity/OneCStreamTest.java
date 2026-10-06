package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Поток 1С называется в документах контракта номером ТЗ, и этот номер не зависит от порядка значений в коде:
 * по нему дежурный сверяет строку сырья с описанием потока.
 */
class OneCStreamTest {

    @Test
    void specificationNumber_isTheNumberOfTheStreamInContractDocuments() {
        assertThat(OneCStream.POLICIES.specificationNumber()).isEqualTo(1);
        assertThat(OneCStream.LPU_CONTRACTS.specificationNumber()).isEqualTo(5);
        assertThat(OneCStream.CONTRAGENT_SERVICES.specificationNumber()).isEqualTo(10);
    }
}
