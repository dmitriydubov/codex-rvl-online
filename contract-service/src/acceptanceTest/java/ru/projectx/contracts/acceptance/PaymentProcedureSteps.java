package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import org.springframework.jdbc.core.simple.JdbcClient;

public class PaymentProcedureSteps {

    private static final String INVOICE_RETURN = "### Возврат заказа с отклонённым счётом в работу";
    private static final String PAYER_ERASURE = "### Стирание адреса плательщика у заказа с отклонённым счётом";

    private final OperationsProcedures procedures;
    private final IssuanceRecords records;
    private final JdbcClient jdbc;

    public PaymentProcedureSteps(OperationsProcedures procedures, IssuanceRecords records, JdbcClient jdbc) {
        this.procedures = procedures;
        this.records = records;
        this.jdbc = jdbc;
    }

    @Когда("дежурный возвращает заказ с отклонённым счётом договора КМ участника {string} заявки {string} в работу по процедуре ручного возврата")
    public void rejectedInvoiceReturned(String participant, String request) {
        procedures.runForContract(INVOICE_RETURN, records.latestContractId(participant, request));
    }

    @Когда("дежурный стирает адрес плательщика у заказа с отклонённым счётом договора КМ участника {string} заявки {string} по процедуре")
    public void payerErased(String participant, String request) {
        procedures.runForContract(PAYER_ERASURE, records.latestContractId(participant, request));
    }

    @Дано("стенд записывает договору КМ участника {string} заявки {string} ошибку {string} без момента оплаты")
    public void errorWithoutPayment(String participant, String request, String errorCode) {
        int updated = jdbc.sql("""
                update contracts
                   set status = 'ERROR', error_code = :code, error_message = 'ошибка записана стендом', paid_at = null
                 where id = :id
                """)
                .param("code", errorCode)
                .param("id", records.latestContractId(participant, request))
                .update();
        assertThat(updated).as("договор КМ участника %s, которому стенд записал ошибку", participant).isEqualTo(1);
    }
}
