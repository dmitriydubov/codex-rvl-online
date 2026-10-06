package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Перечисление потоков 1С документирует, под каким номером ТЗ каждый поток описан в документах контракта.
 * Номер задан явно и не зависит от порядка значений в коде.
 */
class OneCStreamTest {

    @Test
    void specificationNumber_isTheNumberOfTheStreamInContractDocuments() {
        assertThat(OneCStream.POLICIES.specificationNumber()).isEqualTo(1);
        assertThat(OneCStream.LPU_CONTRACTS.specificationNumber()).isEqualTo(5);
        assertThat(OneCStream.CONTRAGENT_SERVICES.specificationNumber()).isEqualTo(10);
    }
}
