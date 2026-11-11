package ru.projectx.contracts.acceptance;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.util.Map;
import ru.projectx.contracts.config.payment.PaymentOrderPassesProperties;
import ru.projectx.contracts.service.payment.invoice.InvoiceIssuing;
import ru.projectx.contracts.service.payment.invoice.InvoiceSummaries;
import ru.projectx.contracts.service.payment.reconciliation.PaymentReconciliation;

public class InvoiceSteps {

    private static final String CHECKOUT = "счёт";
    private static final String STATE = "состояние";
    private static final String UPDATED = "updated";

    private final OrderingStub stub;
    private final InboxSteps inbox;
    private final OrderEventSteps events;
    private final LeaderLockStand leaderLock;
    private final InvoiceIssuing issuing;
    private final PaymentReconciliation reconciliation;
    private final InvoiceSummaries summaries;
    private final PaymentOrderPassesProperties passes;
    private final StandClock clock;

    public InvoiceSteps(OrderingStub stub, InboxSteps inbox, OrderEventSteps events, LeaderLockStand leaderLock,
            InvoiceIssuing issuing, PaymentReconciliation reconciliation, InvoiceSummaries summaries,
            PaymentOrderPassesProperties passes, StandClock clock) {
        this.stub = stub;
        this.inbox = inbox;
        this.events = events;
        this.leaderLock = leaderLock;
        this.issuing = issuing;
        this.reconciliation = reconciliation;
        this.summaries = summaries;
        this.passes = passes;
        this.clock = clock;
    }

    @Дано("Ordering отвечает на выставление счёта: {string}")
    public void checkoutAnswers(String answers) {
        stub.answer(CHECKOUT, answers);
    }

    @Дано("Ordering отвечает на запрос состояния заказа: {string}")
    public void stateAnswers(String answers) {
        stub.answer(STATE, answers);
    }

    @Дано("Ordering выставляет счёт {string} со ссылкой {string} и отвечает только после того, как Договоры разобрали присланные сообщения")
    public void answersAfterTheServiceProcessedMessages(String invoiceId, String paymentUrl) {
        stub.invoiceThenAnswerAfter(invoiceId, paymentUrl, inbox::everythingProcessed);
    }

    @Дано("Ordering выставляет счёт {string} со ссылкой {string} и отвечает только после того, как другой экземпляр Договоров перехватил роль ведущего")
    public void answersAfterAnotherInstanceTookOver(String invoiceId, String paymentUrl) {
        stub.invoiceThenAnswerAfter(invoiceId, paymentUrl, leaderLock::takeOver);
    }

    @Дано("Ordering выставляет счёт {string} со ссылкой {string} и отвечает только после того, как Ordering опубликовал событие {string} типа {string} с updated {string} и Договоры разобрали присланные сообщения")
    public void answersAfterTheEventWasProcessed(String invoiceId, String paymentUrl, String eventId, String type,
            String updated) {
        stub.invoiceThenAnswerAfter(invoiceId, paymentUrl, () -> {
            events.eventPublishedWith(eventId, type, stub.onlyOrder().orderId(), Map.of(UPDATED, updated));
            inbox.everythingProcessed();
        });
    }

    @Когда("заказ {string} в Ordering без события перешёл в статус {string}")
    public void orderEntered(String orderId, String status) {
        order(orderId).enter(status);
    }

    @Когда("заказ {string} в Ordering без события перешёл в статус {string}, событие истории {string} от {string}")
    public void orderEnteredWithHistory(String orderId, String status, String eventType, String moment) {
        StubOrder order = order(orderId);
        order.enter(status);
        order.remember(eventType, moment);
    }

    @Когда("Договоры выставляют счета")
    public void issuingPass() {
        issuing.issueDue();
    }

    @Когда("Договоры сверяют заказы на оплату с Ordering")
    public void reconciliationPass() {
        reconciliation.reconcileDue();
    }

    @Когда("Договоры обновляют сводку счетов и оплат")
    public void summariesRefreshed() {
        summaries.refresh();
    }

    @Когда("на часах Договоров прошёл порог сверки заказов на оплату")
    public void reconciliationThresholdPassed() {
        clock.advance(passes.reconciliation().threshold());
    }

    private StubOrder order(String orderId) {
        return stub.order(orderId)
                .orElseThrow(() -> new IllegalArgumentException("у заглушки Ordering нет заказа " + orderId));
    }
}
