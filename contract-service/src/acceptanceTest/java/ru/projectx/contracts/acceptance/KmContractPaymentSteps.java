package ru.projectx.contracts.acceptance;

import io.cucumber.java.ru.Дано;
import java.time.Instant;
import ru.projectx.contracts.service.payment.ContractPayments;

public class KmContractPaymentSteps {

    private final ContractPayments payments;
    private final IssuanceRecords records;

    public KmContractPaymentSteps(ContractPayments payments, IssuanceRecords records) {
        this.payments = payments;
        this.records = records;
    }

    @Дано("договор КМ участника {string} заявки {string} оплачен, момент оплаты {string}")
    public void contractPaid(String participantId, String requestId, String paidAt) {
        payments.markPaid(records.latestContractId(participantId, requestId), Instant.parse(paidAt));
    }
}
