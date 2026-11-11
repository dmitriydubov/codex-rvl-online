package ru.projectx.contracts.acceptance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class PaymentOrderRecords {

    private static final String CREATED = "CREATED";
    private static final Map<String, String> STATES = Map.of(
            "AWAITING_CREATION", "ждёт создания",
            CREATED, "создан",
            "REJECTED", "отклонён",
            "CANCEL_PENDING", "к отмене",
            "CANCELLED", "отменён",
            "CANCEL_REJECTED", "отмена отклонена",
            "EXPIRED", "срок истёк");
    private static final Map<String, String> INVOICE_STATES = Map.of(
            "AWAITING_DOCUMENT", "создан",
            "DOCUMENT_UNCONFIRMED", "создан",
            "READY", "готов к оплате",
            "CHECKOUT", "готов к оплате",
            "ISSUED", "счёт выставлен",
            "REJECTED", "счёт отклонён",
            "PAYMENT_ERROR", "ошибка оплаты",
            "PAID", "оплачен",
            "OVERDUE", "просрочен");
    private static final List<String> TEXTS = List.of("id", "contract_id", "client_id", "correlation_id", "state",
            "external_order_id", "rejection_code", "contract_number", "amount", "creation_requested", "cancel_requested",
            "payer_email", "invoice_state", "fix_repeats", "invoice_id", "payment_url");
    private static final List<String> MOMENTS = List.of("checkout_started_at", "payment_deadline", "paid_at");
    private static final String ROWS = """
            select o.id::text as id, o.contract_id::text as contract_id, o.client_id::text as client_id,
                   o.correlation_id::text as correlation_id, o.state::text as state, o.external_order_id,
                   coalesce(o.rejection_code, i.rejection_code) as rejection_code, o.contract_number,
                   o.amount::text as amount, o.creation_requested::text as creation_requested,
                   o.cancel_requested::text as cancel_requested, o.payer_email, i.state::text as invoice_state,
                   i.fix_repeats::text as fix_repeats, i.invoice_id, i.payment_url, i.checkout_started_at,
                   i.payment_deadline, i.paid_at
              from payment_orders o
              left join payment_invoices i on i.payment_order_id = o.id
            """;

    private final JdbcClient jdbc;
    private final IssuanceRecords records;

    public PaymentOrderRecords(JdbcClient jdbc, IssuanceRecords records) {
        this.jdbc = jdbc;
        this.records = records;
    }

    List<Map<String, String>> ofContract(String participantId, String requestId) {
        UUID contractId = records.latestContractId(participantId, requestId);
        return jdbc.sql(ROWS + " where o.contract_id = ?").param(contractId).query(PaymentOrderRecords::row).list();
    }

    Map<String, String> single(String participantId, String requestId) {
        List<Map<String, String>> orders = ofContract(participantId, requestId);
        if (orders.size() != 1) {
            throw new AssertionError("у договора КМ участника " + participantId + " заказов на оплату " + orders.size());
        }
        return orders.getFirst();
    }

    Map<String, String> onlyOrder() {
        List<Map<String, String>> orders = jdbc.sql(ROWS).query(PaymentOrderRecords::row).list();
        if (orders.size() != 1) {
            throw new AssertionError("«как у заказа» требует одного заказа на оплату на стенде, их " + orders.size());
        }
        return orders.getFirst();
    }

    static String state(Map<String, String> order) {
        String state = order.get("state");
        return Optional.ofNullable(order.get("invoice_state"))
                .filter(invoice -> CREATED.equals(state))
                .map(INVOICE_STATES::get)
                .orElseGet(() -> STATES.getOrDefault(state, state));
    }

    private static Map<String, String> row(ResultSet row, int number) throws SQLException {
        Map<String, String> values = new HashMap<>();
        for (String column : TEXTS) {
            values.put(column, row.getString(column));
        }
        for (String column : MOMENTS) {
            values.put(column, Optional.ofNullable(row.getObject(column, OffsetDateTime.class))
                    .map(moment -> moment.toInstant().toString())
                    .orElse(null));
        }
        return values;
    }
}
