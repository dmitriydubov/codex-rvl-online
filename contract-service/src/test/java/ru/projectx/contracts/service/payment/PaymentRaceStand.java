package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.contract.AcceptanceChannel;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractDraft;
import ru.projectx.contracts.entity.contract.ContractStep;
import ru.projectx.contracts.entity.contract.OfferAcceptance;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.issuance.RequestReference;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.leadership.LeaderLock;
import ru.projectx.contracts.service.payment.event.PaymentOrderEventHandler;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

final class PaymentRaceStand {

    static final Duration WAIT = Duration.ofSeconds(20);
    static final String EMAIL = "insured@example.test";

    private static final String EVENT = """
            {"eventId": "%s", "eventType": "%s", "orderId": "%s", "updated": "%s",
             "orderDetails": [{"product": "rvl", "documentId": "%s", "documentPremium": 34000.00}]}
            """;
    private static final String CONTRACT_ROW = "select status::text as status, paid_at, error_code,"
            + " cancel_reason::text as cancel_reason, cancelled_at, entity_version from contracts where id = ?";
    private static final String ORDER_ROW = "select id, state::text as state, external_order_id, payer_email"
            + " from payment_orders where contract_id = ?";
    private static final String INVOICE_ROW = "select state::text as state, checkout_started_at, payment_deadline,"
            + " invoice_id, payment_url, paid_at, rejection_code, reply_lost_reported,"
            + " deferred_cancel_reason::text as deferred_cancel_reason, deferred_cancelled_at"
            + " from payment_invoices where contract_id = ?";

    @TestConfiguration(proxyBeanMethods = false)
    static class Scripted {

        @Bean
        @Primary
        ScriptedOrdering scriptedOrdering() {
            return new ScriptedOrdering();
        }
    }

    private final ContractRepository contracts;
    private final PaymentOrderOpening openings;
    private final RequestStatusPublication statuses;
    private final PaymentOrderEventHandler events;
    private final TransactionTemplate transaction;
    private final JdbcClient jdbc;
    private final BackgroundLeadership leadership;
    private final LeaderLock lock;
    private final Clock clock;

    PaymentRaceStand(ContractRepository contracts, PaymentOrderOpening openings, RequestStatusPublication statuses,
            PaymentOrderEventHandler events, TransactionTemplate transaction, JdbcClient jdbc,
            BackgroundLeadership leadership, LeaderLock lock, Clock clock) {
        this.contracts = contracts;
        this.openings = openings;
        this.statuses = statuses;
        this.events = events;
        this.transaction = transaction;
        this.jdbc = jdbc;
        this.leadership = leadership;
        this.lock = lock;
        this.clock = clock;
    }

    UUID acceptedContractWithOrder(UUID request) {
        IssuanceTerms terms = new IssuanceTerms(new RequestReference(request, Tasks.INSURANCE_CONTRACT,
                Tasks.INSURER_POLICY), Tasks.INSURED, Tasks.PROGRAM, Issuances.PERIOD);
        return transaction.execute(status -> {
            IssuanceSnapshot before = statuses.watch(request);
            Contract contract = Contract.open(new ContractDraft("DKM-PAY-" + request.toString().substring(0, 8), terms,
                    Tasks.SPOUSE, new BigDecimal("34000.00")), Issuances.OPENED);
            contract.offerDraft();
            contract.advance(ContractStep.OFFER_ACCEPTED);
            Contract saved = contracts.saveAndFlush(contract);
            openings.forPayer(EMAIL).open(saved, new OfferAcceptance(1, Tasks.INSURED, Issuances.OPENED,
                    AcceptanceChannel.WEB, null, null), Issuances.OPENED);
            statuses.afterTask(before, request, List.of(new RequestParticipant(Tasks.INSURED, DocumentKind.NONE),
                    new RequestParticipant(Tasks.SPOUSE, DocumentKind.KM_CONTRACT)), Issuances.OPENED);
            return saved.id();
        });
    }

    Optional<String> orderingEvent(UUID contractId, String eventType, Instant updated) {
        Map<String, Object> order = order(contractId);
        String payload = EVENT.formatted(UUID.randomUUID(), eventType, order.get("external_order_id"),
                OffsetDateTime.ofInstant(updated, ZoneOffset.ofHours(3)), order.get("id"));
        return transaction.execute(status -> events.prepare(payload).applyOrSkip(clock.instant()));
    }

    void makeInvoiceStale(UUID contractId) {
        assertThat(jdbc.sql("update payment_invoices set updated_at = updated_at - interval '1 hour'"
                + " where contract_id = ?").param(contractId).update()).isEqualTo(1);
    }

    void makeCheckoutAbandoned(UUID contractId) {
        assertThat(jdbc.sql("update payment_invoices set checkout_started_at = checkout_started_at - interval '1 hour',"
                + " updated_at = updated_at - interval '1 hour' where contract_id = ?").param(contractId).update())
                .isEqualTo(1);
    }

    Map<String, Object> contract(UUID contractId) {
        return jdbc.sql(CONTRACT_ROW).param(contractId).query().singleRow();
    }

    Map<String, Object> order(UUID contractId) {
        return jdbc.sql(ORDER_ROW).param(contractId).query().singleRow();
    }

    Map<String, Object> invoice(UUID contractId) {
        return jdbc.sql(INVOICE_ROW).param(contractId).query().singleRow();
    }

    long statusMessages(UUID request) {
        return jdbc.sql("select count(*) from outbox_messages where aggregate_type = 'INDIVIDUAL_REQUEST'"
                + " and aggregate_id = ?").param(request).query(Long.class).single();
    }

    String lastStatusMessage(UUID request) {
        return jdbc.sql("select payload::text from outbox_messages where aggregate_type = 'INDIVIDUAL_REQUEST'"
                + " and aggregate_id = ? order by entity_version desc limit 1").param(request).query(String.class)
                .single();
    }

    long contractsOf(UUID request) {
        return jdbc.sql("select count(*) from contracts where individual_request_id = ?").param(request)
                .query(Long.class).single();
    }

    long policiesOf(UUID request) {
        return jdbc.sql("select count(*) from lm_policies where individual_request_id = ?").param(request)
                .query(Long.class).single();
    }

    void handLeadershipTo(String instanceId) {
        jdbc.sql("update leader_lock set client_id = ?, created_date = now() where region = ? and lock_key = ?")
                .param(instanceId).param(lock.group()).param(lock.key()).update();
    }

    void returnLeadership() {
        handLeadershipTo(lock.instanceId());
        awaitLeadership();
    }

    void awaitLeadership() {
        await().atMost(WAIT).until(leadership::leader);
    }

    void remove(UUID request) {
        String ofRequest = " in (select id from contracts where individual_request_id = :request)";
        jdbc.sql("delete from outbox_messages where aggregate_id = :request or aggregate_id in"
                + " (select id from payment_orders where contract_id" + ofRequest + ")").param("request", request)
                .update();
        jdbc.sql("delete from payment_invoices where contract_id" + ofRequest).param("request", request).update();
        jdbc.sql("delete from payment_orders where contract_id" + ofRequest).param("request", request).update();
        jdbc.sql("delete from contract_persons where contract_id" + ofRequest).param("request", request).update();
        jdbc.sql("delete from contracts where individual_request_id = :request").param("request", request).update();
        jdbc.sql("delete from request_participants where individual_request_id = :request").param("request", request)
                .update();
        jdbc.sql("delete from issuance_requests where individual_request_id = :request").param("request", request)
                .update();
    }
}
