package ru.projectx.contracts.entity.payment;

public record InvoiceLink(String invoiceId, String paymentUrl) {

    @Override
    public String toString() {
        return "InvoiceLink[invoiceId=" + invoiceId + ", ссылка скрыта]";
    }
}
