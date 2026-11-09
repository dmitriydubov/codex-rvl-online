package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.config.broker.MarketingBrokerProperties;
import ru.projectx.contracts.config.payment.OrderingProperties;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;
import ru.projectx.contracts.service.integration.ordering.OrderingHttpClient;
import ru.projectx.contracts.service.payment.cancellation.PaymentOrderCancelling;
import ru.projectx.contracts.service.payment.order.PaymentOrderAwaiting;
import ru.projectx.contracts.service.payment.order.PaymentOrderCreation;

public class PaymentOrderSteps {

    private static final Set<String> SETTINGS = Set.of("продукт", "канал", "причина отмены", "обменник подтверждений",
            "ключ подтверждения", "клиент сервера токенов", "попыток вызова");
    private static final Set<String> ORDER_FIELDS = Set.of("состояние", "orderId", "clientId", "код отказа");
    private static final String NONE = "нет";
    private static final Path OPERATIONS = Path.of("docs", "operations.md");
    private static final String RETURN_SECTION = "### Возврат отклонённого заказа на оплату в работу";
    private static final String SQL_START = "```sql\n";
    private static final String SQL_END = "```";
    private static final String CONTRACT_ID = "<contract-id>";

    private final OrderingStub stub;
    private final OrderingProperties ordering;
    private final MarketingBrokerProperties marketing;
    private final PaymentOrderPassesProperties passes;
    private final OrderingHttpClient client;
    private final DocumentFixQueue fixes;
    private final PaymentOrderCreation creation;
    private final PaymentOrderCancelling cancelling;
    private final PaymentOrderAwaiting awaiting;
    private final PaymentOrderRecords orders;
    private final IssuanceRecords records;
    private final ServiceJournal journal;
    private final StandClock clock;
    private final JdbcClient jdbc;

    public PaymentOrderSteps(OrderingStub stub, OrderingProperties ordering, MarketingBrokerProperties marketing,
            PaymentOrderPassesProperties passes, OrderingHttpClient client, DocumentFixQueue fixes,
            PaymentOrderCreation creation, PaymentOrderCancelling cancelling, PaymentOrderAwaiting awaiting,
            PaymentOrderRecords orders, IssuanceRecords records, ServiceJournal journal, StandClock clock,
            JdbcClient jdbc) {
        this.stub = stub;
        this.ordering = ordering;
        this.marketing = marketing;
        this.passes = passes;
        this.client = client;
        this.fixes = fixes;
        this.creation = creation;
        this.cancelling = cancelling;
        this.awaiting = awaiting;
        this.orders = orders;
        this.records = records;
        this.journal = journal;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Дано("оплата договоров КМ через Ordering на стенде включена:")
    public void paymentEnabled(Map<String, String> settings) {
        StepTables.onlyKnown(settings, SETTINGS);
        assertThat(Map.of(
                "продукт", ordering.product(),
                "канал", ordering.channel(),
                "причина отмены", ordering.cancelReason(),
                "обменник подтверждений", marketing.documentFixes().exchange(),
                "ключ подтверждения", marketing.documentFixes().routingKey(),
                "клиент сервера токенов", ordering.token().clientId(),
                "попыток вызова", String.valueOf(ordering.retry().attempts())))
                .as("настройки оплаты на стенде").containsAllEntriesOf(settings);
        stub.reset();
        Object tokens = ReflectionTestUtils.getField(client, "tokens");
        ReflectionTestUtils.invokeMethod(tokens, "discard");
        fixes.declare();
        fixes.purge();
    }

    @Когда("Договоры создают заказы на оплату")
    public void creationPass() {
        creation.createDue();
    }

    @Когда("Договоры отменяют заказы на оплату")
    public void cancellationPass() {
        cancelling.cancelDue();
    }

    @Когда("Договоры обновляют сводку договоров КМ без заказа на оплату")
    public void awaitingSummary() {
        awaiting.refresh();
    }

    @Когда("на часах Договоров прошёл порог ожидания заказа на оплату")
    public void awaitingThresholdPassed() {
        clock.advance(passes.awaitingThreshold());
    }

    @Дано("у брокера маркетинга удалена очередь подтверждений документов")
    public void fixesQueueDeleted() {
        fixes.delete();
    }

    @io.cucumber.java.After
    public void fixesQueueRestored() {
        fixes.declare();
    }

    @Тогда("заказ на оплату договора КМ участника {string} заявки {string}:")
    public void orderIs(String participant, String request, Map<String, String> expected) {
        StepTables.onlyKnown(expected, ORDER_FIELDS);
        Map<String, String> order = orders.single(participant, request);
        Map<String, String> actual = Map.of(
                "состояние", PaymentOrderRecords.state(order),
                "orderId", orNone(order.get("external_order_id")),
                "clientId", order.get("client_id"),
                "код отказа", orNone(order.get("rejection_code")));
        expected.forEach((field, value) -> assertThat(actual.get(field))
                .as("поле «%s» заказа на оплату договора КМ участника %s", field, participant).isEqualTo(value));
    }

    @Тогда("заказов на оплату у договора КМ участника {string} заявки {string} — {int}")
    public void orderCount(String participant, String request, int count) {
        assertThat(orders.ofContract(participant, request)).as("заказы на оплату договора КМ участника %s", participant)
                .hasSize(count);
    }

    @Тогда("в журнале Договоров есть запись уровня {word} по заказу на оплату договора КМ участника {string} заявки {string} с причиной {string}")
    public void orderLogged(String level, String participant, String request, String reason) {
        String expected = "заказ на оплату договора КМ " + records.latestContractId(participant, request) + " участника "
                + participant + " заявки " + request + ": " + reason;
        String correlationId = orders.single(participant, request).get("correlation_id");
        List<ILoggingEvent> matching = journal.events().stream()
                .filter(event -> event.getLevel() == Level.toLevel(level, null))
                .filter(event -> event.getFormattedMessage().equals(expected))
                .toList();
        assertThat(matching).as("записи %s «%s»", level, expected).isNotEmpty().allSatisfy(event -> {
            assertThat(event.getMDCPropertyMap()).as("ключи MDC").containsEntry("correlationId", correlationId);
            assertThat(event.getThrowableProxy()).as("приложенное исключение").isNull();
        });
    }

    @Тогда("секрет клиента сервера токенов не попал в журнал Договоров")
    public void secretNotLogged() {
        String secret = ordering.token().clientSecret();
        assertThat(secret).as("секрет клиента на стенде").isNotBlank();
        assertThat(journal.mentioning(secret)).as("записи журнала с секретом клиента").isEmpty();
    }

    @Тогда("текст ошибки Ordering {string} не попал ни в журнал Договоров, ни в заказ на оплату")
    public void errorTextNotKept(String text) {
        assertThat(text).isNotBlank();
        assertThat(journal.mentioning(text)).as("записи журнала с текстом ошибки Ordering").isEmpty();
        List<String> stored = jdbc.sql("select concat_ws('|', external_order_id, rejection_code, contract_number)"
                + " from payment_orders").query(String.class).list();
        assertThat(stored).as("поля заказов на оплату").noneMatch(value -> value.contains(text));
    }

    @Когда("дежурный возвращает заказ на оплату договора КМ участника {string} заявки {string} в работу по процедуре ручного возврата")
    public void returnedByProcedure(String participant, String request) throws IOException {
        String contractId = records.latestContractId(participant, request).toString();
        jdbc.sql(procedure().replace(CONTRACT_ID, contractId)).update();
    }

    private static String orNone(String value) {
        return Optional.ofNullable(value).orElse(NONE);
    }

    private static String procedure() throws IOException {
        String operations = Files.readString(OPERATIONS, StandardCharsets.UTF_8);
        int section = operations.indexOf(RETURN_SECTION);
        assertThat(section).as("раздел «%s» в %s", RETURN_SECTION, OPERATIONS).isNotNegative();
        int start = operations.indexOf(SQL_START, section) + SQL_START.length();
        return operations.substring(start, operations.indexOf(SQL_END, start)).strip();
    }
}
