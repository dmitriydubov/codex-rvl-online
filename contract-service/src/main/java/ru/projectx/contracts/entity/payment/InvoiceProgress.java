package ru.projectx.contracts.entity.payment;

record InvoiceProgress(InvoiceState state, int fixRepeats, boolean lostReplyReported, boolean linked) {
}
