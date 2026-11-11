package ru.projectx.contracts.entity.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvoiceLinkTest {

    private static final InvoiceLink LINK = new InvoiceLink("inv-0001", "https://pay.example.test/inv-0001?key=stand");

    @Test
    void textForm_namesTheInvoiceAndHidesThePaymentLink() {
        assertThat(LINK.toString()).isEqualTo("InvoiceLink[invoiceId=inv-0001, ссылка скрыта]");
        assertThat(String.valueOf(LINK)).doesNotContain("pay.example.test").doesNotContain("key=stand");
    }

    @Test
    void paymentLink_isKeptForTheReplyToRequests() {
        assertThat(LINK.paymentUrl()).isEqualTo("https://pay.example.test/inv-0001?key=stand");
        assertThat(LINK.invoiceId()).isEqualTo("inv-0001");
    }
}
