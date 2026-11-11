package ru.projectx.contracts.entity.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PayerEmailTest {

    @Test
    void textForm_hidesTheAddress() {
        PayerEmail payer = new PayerEmail("insured@example.test");

        assertThat(payer.toString()).isEqualTo("PayerEmail[адрес скрыт]");
        assertThat(String.valueOf(payer)).doesNotContain("insured").doesNotContain("example.test");
    }

    @Test
    void address_isKeptForTheCheckoutRequest() {
        assertThat(new PayerEmail("insured@example.test").address()).isEqualTo("insured@example.test");
    }
}
