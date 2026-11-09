package ru.projectx.contracts.acceptance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class PaymentOrderRecords {

    private static final Map<String, String> STATES = Map.of(
            "AWAITING_CREATION", "ждёт создания",
            "CREATED", "создан",
            "REJECTED", "отклонён",
            "CANCEL_PENDING", "к отмене",
            "CANCELLED", "отменён",
            "CANCEL_REJECTED", "отмена отклонена");
    private static final String ROWS = """
            select id::text as id, contract_id::text as contract_id, client_id::text as client_id,
                   correlation_id::text as correlation_id, state::text as state, external_order_id, rejection_code,
                   contract_number, amount::text as amount, creation_requested::text as creation_requested,
                   cancel_requested::text as cancel_requested
              from payment_orders
            """;

    private final JdbcClient jdbc;
    private final IssuanceRecords records;

    public PaymentOrderRecords(JdbcClient jdbc, IssuanceRecords records) {
        this.jdbc = jdbc;
        this.records = records;
    }

    List<Map<String, String>> ofContract(String participantId, String requestId) {
        UUID contractId = records.latestContractId(participantId, requestId);
        return jdbc.sql(ROWS + " where contract_id = ?").param(contractId).query(PaymentOrderRecords::row).list();
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
        return STATES.getOrDefault(order.get("state"), order.get("state"));
    }

    private static Map<String, String> row(java.sql.ResultSet row, int number) throws java.sql.SQLException {
        Map<String, String> values = new HashMap<>();
        for (String column : List.of("id", "contract_id", "client_id", "correlation_id", "state", "external_order_id",
                "rejection_code", "contract_number", "amount", "creation_requested", "cancel_requested")) {
            values.put(column, row.getString(column));
        }
        return values;
    }
}
