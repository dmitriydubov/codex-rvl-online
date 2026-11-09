package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import ru.projectx.contracts.entity.contract.AcceptanceChannel;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractDraft;
import ru.projectx.contracts.entity.contract.OfferAcceptance;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;
import ru.projectx.contracts.entity.issuance.RequestReference;
import ru.projectx.contracts.message.task.ContractTaskCancellation;
import ru.projectx.contracts.repository.issuance.ContractRepository;
import ru.projectx.contracts.service.cancellation.TaskCancellationService;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.leadership.LeaderLock;
import ru.projectx.contracts.service.payment.cancellation.PaymentOrderCancelling;
import ru.projectx.contracts.service.payment.order.PaymentOrderCreation;
import ru.projectx.contracts.service.payment.order.PaymentOrderRecording;
import ru.projectx.contracts.service.payment.order.RejectedDocumentFixes;
import ru.projectx.contracts.support.IntegrationTest;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;

@IntegrationTest
@TestPropertySource(properties = {
        "projectx.payment.enabled=true",
        "projectx.ordering.url=http://ordering.test",
        "projectx.ordering.product=rvl",
        "projectx.ordering.channel=rvl-lk",
        "projectx.ordering.retry.delay=0s",
        "projectx.ordering.token.uri=http://keycloak.test/token",
        "projectx.ordering.token.client-id=contracts-test",
        "projectx.ordering.token.client-secret=no-keycloak",
        "projectx.brokers.marketing.connection.host=marketing.test",
        "projectx.brokers.marketing.connection.username=contracts-test",
        "projectx.brokers.marketing.connection.password=no-broker",
        "projectx.brokers.marketing.document-fixes.exchange=rvl.exchange",
        "projectx.brokers.marketing.document-fixes.routing-key=rvl-contract-fixed"})
@Import(PaymentOrderRaceIT.Scripted.class)
class PaymentOrderRaceIT {

    private static final Duration WAIT = Duration.ofSeconds(20);
    private static final String ORDER_ROW = "select state::text as state, external_order_id, creation_requested"
            + " from payment_orders where contract_id = ?";
    private static final String LOCK_WAITS = "select count(*) from pg_stat_activity"
            + " where wait_event_type = 'Lock' and datname = current_database()"
            + " and application_name <> 'contract-service-leader-lock'";
    private static final String FIXES = "select count(*) from outbox_messages where aggregate_type = 'PAYMENT_ORDER'"
            + " and aggregate_id = (select id from payment_orders where contract_id = ?)";

    @TestConfiguration(proxyBeanMethods = false)
    static class Scripted {

        @Bean
        @Primary
        ScriptedOrdering scriptedOrdering() {
            return new ScriptedOrdering();
        }
    }

    @Autowired
    private ScriptedOrdering ordering;

    @Autowired
    private PaymentOrderCreation creation;

    @Autowired
    private PaymentOrderOpening openings;

    @Autowired
    private TaskCancellationService cancellation;

    @Autowired
    private ContractRepository contracts;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private BackgroundLeadership leadership;

    @Autowired
    private RejectedDocumentFixes rejectedFixes;

    @Autowired
    private PaymentOrderCancelling cancelling;

    @Autowired
    private PaymentOrderRecording recording;

    @Autowired
    private MeterRegistry meters;

    @Autowired
    private LeaderLock lock;

    private final UUID request = UUID.randomUUID();
    private UUID contractId;

    @BeforeEach
    void acceptedContractWithAwaitingOrder() {
        ordering.reset();
        await().atMost(WAIT).until(leadership::leader);
        IssuanceTerms terms = new IssuanceTerms(new RequestReference(request, Tasks.INSURANCE_CONTRACT,
                Tasks.INSURER_POLICY), Tasks.INSURED, Tasks.PROGRAM, Issuances.PERIOD);
        contractId = transaction.execute(status -> {
            Contract contract = Contract.open(new ContractDraft("DKM-PAY-" + request.toString().substring(0, 8), terms,
                    Tasks.SPOUSE, new BigDecimal("34000.00")), Issuances.OPENED);
            contract.offerDraft();
            contract.accept();
            Contract saved = contracts.saveAndFlush(contract);
            openings.open(saved, new OfferAcceptance(1, Tasks.INSURED, Issuances.OPENED, AcceptanceChannel.WEB, null,
                    null), Issuances.OPENED);
            return saved.id();
        });
    }

    @AfterEach
    void removeRecords() {
        jdbc.sql("delete from outbox_messages where aggregate_type = 'PAYMENT_ORDER'").update();
        jdbc.sql("delete from payment_orders where contract_id = ?").param(contractId).update();
        contracts.deleteById(contractId);
    }

    @Test
    void cancellationCommittedWhileOrderingCreatesTheOrder_keepsTheOrderIdForCancellationWithoutDocumentFix() {
        ordering.duringCreation(() -> transaction.executeWithoutResult(status -> cancellation.apply(
                new ContractTaskCancellation(request, Instant.parse("2026-02-12T11:30:00Z"), "INSURED_REFUSED", null),
                Instant.parse("2026-02-12T11:30:00Z"))));

        creation.createDue();

        assertThat(contracts.findById(contractId).orElseThrow().status().name()).isEqualTo("CANCELLED");
        Map<String, Object> order = orderRow();
        assertThat(order.get("state")).isEqualTo("CANCEL_PENDING");
        assertThat(order.get("external_order_id")).isEqualTo("order-1");
        assertThat(fixes()).isZero();
    }

    @Test
    void twoPassesOverOneContractAtOnce_createOneOrderAndOneDocumentFix() throws Exception {
        CountDownLatch firstSending = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        ordering.duringCreation(() -> {
            firstSending.countDown();
            awaitQuietly(secondDone);
        });
        CompletableFuture<Integer> first = CompletableFuture.supplyAsync(creation::createDue);
        assertThat(firstSending.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        creation.createDue();
        secondDone.countDown();
        first.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        assertThat(ordering.creations()).isEqualTo(1);
        assertThat(orderRow().get("state")).isEqualTo("CREATED");
        assertThat(orderRow().get("external_order_id")).isEqualTo("order-1");
        assertThat(fixes()).isEqualTo(1);
    }

    @Test
    void leadershipLostBetweenCreationAndItsRecord_writesNothingAndTheNextPassFindsTheOrder() {
        ordering.duringCreation(() -> handLeadership("stand-other-instance"));

        creation.createDue();

        Map<String, Object> lost = orderRow();
        assertThat(lost.get("state")).isEqualTo("AWAITING_CREATION");
        assertThat(lost.get("external_order_id")).isNull();
        assertThat(lost.get("creation_requested")).isEqualTo(true);
        assertThat(fixes()).isZero();

        ordering.duringCreation(() -> { });
        handLeadership(lock.instanceId());
        await().atMost(WAIT).until(leadership::leader);
        creation.createDue();

        assertThat(ordering.creations()).isEqualTo(1);
        assertThat(orderRow().get("state")).isEqualTo("CREATED");
        assertThat(fixes()).isEqualTo(1);
    }

    @Test
    void undeliveredFixWaitingForTheContractWhileCancellationCommits_keepsTheOrderForCancellation() throws Exception {
        creation.createDue();
        UUID orderId = jdbc.sql("select id from payment_orders where contract_id = ?").param(contractId)
                .query(UUID.class).single();
        double rejectedBefore = rejectedOrders();
        CountDownLatch cancellationLocked = new CountDownLatch(1);
        CountDownLatch commitCancellation = new CountDownLatch(1);
        CompletableFuture<Void> cancelling = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
            cancellation.apply(new ContractTaskCancellation(request, Instant.parse("2026-02-12T11:30:00Z"),
                    "INSURED_REFUSED", null), Instant.parse("2026-02-12T11:30:00Z"));
            cancellationLocked.countDown();
            awaitQuietly(commitCancellation);
        }));
        assertThat(cancellationLocked.await(WAIT.toSeconds(), TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> rejecting = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(
                status -> rejectedFixes.rejected(orderId, Instant.parse("2026-02-12T11:31:00Z"))));
        await().atMost(WAIT).until(() -> jdbc.sql(LOCK_WAITS).query(Long.class).single() > 0);
        commitCancellation.countDown();
        cancelling.get(WAIT.toSeconds(), TimeUnit.SECONDS);
        rejecting.get(WAIT.toSeconds(), TimeUnit.SECONDS);

        Map<String, Object> order = orderRow();
        assertThat(order.get("state")).isEqualTo("CANCEL_PENDING");
        assertThat(order.get("external_order_id")).isEqualTo("order-1");
        assertThat(rejectedOrders()).isEqualTo(rejectedBefore);
    }

    @Test
    void orderIdRecordedWhileTheCancellationPassFoundNothing_keepsTheRecordAndTheNextPassCancelsTheOrder() {
        jdbc.sql("update payment_orders set state = 'CANCEL_PENDING', creation_requested = true where contract_id = ?")
                .param(contractId).update();
        ordering.duringSearch(() -> {
            ordering.duringSearch(() -> { });
            transaction.executeWithoutResult(status -> recording.created(contractId, "order-late"));
        });

        cancelling.cancelDue();

        Map<String, Object> kept = orderRow();
        assertThat(kept.get("state")).isEqualTo("CANCEL_PENDING");
        assertThat(kept.get("external_order_id")).isEqualTo("order-late");
        assertThat(ordering.cancelled()).isEmpty();

        cancelling.cancelDue();

        assertThat(orderRow().get("state")).isEqualTo("CANCELLED");
        assertThat(ordering.cancelled()).containsExactly("order-late");
        assertThat(fixes()).isZero();
    }

    private double rejectedOrders() {
        return Optional.ofNullable(meters.find("contracts.payment.orders.rejected").counter()).map(Counter::count)
                .orElse(0.0);
    }

    private void handLeadership(String instanceId) {
        jdbc.sql("update leader_lock set client_id = ?, created_date = now() where region = ? and lock_key = ?")
                .param(instanceId).param(lock.group()).param(lock.key()).update();
    }

    private Map<String, Object> orderRow() {
        return jdbc.sql(ORDER_ROW).param(contractId).query().singleRow();
    }

    private long fixes() {
        return jdbc.sql(FIXES).param(contractId).query(Long.class).single();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(WAIT.toSeconds(), TimeUnit.SECONDS);
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
