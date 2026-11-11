package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.payment.InvoiceState;
import ru.projectx.contracts.entity.payment.PaymentOrder;
import ru.projectx.contracts.entity.payment.PaymentOrderState;
import ru.projectx.contracts.repository.payment.PaymentOrderRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.leadership.LeadershipLostException;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.OrderingTestProperties;
import ru.projectx.contracts.support.PaymentOrders;

class PaymentOrderPassesTest {

    private final PaymentOrderRepository orders = mock();
    private final BackgroundLeadership leadership = mock();
    private final OrderingStepRunner steps = mock();
    private final PaymentOrderPasses passes =
            new PaymentOrderPasses(orders, leadership, steps, OrderingTestProperties.passes());
    private final List<UUID> handled = new ArrayList<>();
    private final PaymentOrder first = PaymentOrders.awaiting();
    private final PaymentOrder second = PaymentOrders.awaiting();
    private CapturedLog log;

    @BeforeEach
    void leaderWithTwoOrders() {
        log = CapturedLog.of(PaymentOrderPasses.class);
        ReflectionTestUtils.setField(second, "id", UUID.fromString("0196f0a2-0000-7000-8000-000000000022"));
        given(leadership.countAsLeader(any())).willAnswer(pass -> pass.<IntSupplier>getArgument(0).getAsInt());
        given(orders.findByStateOrderByCreatedAtAscIdAsc(PaymentOrderState.AWAITING_CREATION,
                Limit.of(OrderingTestProperties.BATCH))).willReturn(List.of(first, second));
    }

    @AfterEach
    void releaseLog() {
        log.close();
    }

    @Test
    void everyOrder_isHandledWithItsCorrelationIdInTheLogContext() {
        int count = passes.eachAsLeader(PaymentOrderState.AWAITING_CREATION, order -> {
            assertThat(MDC.get("correlationId")).isEqualTo(order.correlationId().toString());
            handled.add(order.id());
            return true;
        });

        assertThat(count).isEqualTo(2);
        assertThat(handled).containsExactly(PaymentOrders.ORDER, second.view().id());
    }

    @Test
    void work_runsItsStepForEveryOrderOfItsStateUntilOrderingIsUnavailable() {
        OrderingStep<String> step = new OrderingStep<>("не создан", session -> "o-1", created -> { }, code -> { });
        PaymentOrderWork work = new PaymentOrderWork() {
            @Override
            public PaymentOrderState state() {
                return PaymentOrderState.AWAITING_CREATION;
            }

            @Override
            public OrderingStep<?> step(ru.projectx.contracts.entity.payment.PaymentOrderView order) {
                handled.add(order.id());
                return step;
            }
        };
        given(steps.run(any(), org.mockito.ArgumentMatchers.same(step))).willReturn(false);

        assertThat(passes.run(work)).isEqualTo(1);

        assertThat(handled).containsExactly(PaymentOrders.ORDER);
    }

    @Test
    void work_goesOnToTheNextOrderWhileItsStepsAskToGoOn() {
        OrderingStep<String> step = new OrderingStep<>("не создан", session -> "o-1", created -> { }, code -> { });
        PaymentOrderWork work = new PaymentOrderWork() {
            @Override
            public PaymentOrderState state() {
                return PaymentOrderState.AWAITING_CREATION;
            }

            @Override
            public OrderingStep<?> step(ru.projectx.contracts.entity.payment.PaymentOrderView order) {
                handled.add(order.id());
                return step;
            }
        };
        given(steps.run(any(), org.mockito.ArgumentMatchers.same(step))).willReturn(true);

        assertThat(passes.run(work)).isEqualTo(2);

        assertThat(handled).hasSize(2);
    }

    @Test
    void orderThatAsksToStop_endsThePass() {
        int count = passes.eachAsLeader(PaymentOrderState.AWAITING_CREATION, order -> {
            handled.add(order.id());
            return false;
        });

        assertThat(count).isEqualTo(1);
        assertThat(handled).containsExactly(PaymentOrders.ORDER);
    }

    @Test
    void brokenOrder_isLoggedWithoutDetailsAndTheNextOneIsHandled() {
        passes.eachAsLeader(PaymentOrderState.AWAITING_CREATION, order -> {
            handled.add(order.id());
            if (order.id().equals(PaymentOrders.ORDER)) {
                throw new IllegalStateException("значение поля");
            }
            return true;
        });

        assertThat(handled).hasSize(2);
        assertThat(log.at(Level.WARN)).containsExactly("заказ на оплату договора КМ " + PaymentOrders.CONTRACT
                + " не обработан: java.lang.IllegalStateException, SQLSTATE нет");
    }

    @Test
    void temporaryDatabaseFailure_endsThePass() {
        int count = passes.eachAsLeader(PaymentOrderState.AWAITING_CREATION, order -> {
            handled.add(order.id());
            throw new IllegalStateException(new SQLException("база недоступна", "08006"));
        });

        assertThat(count).isEqualTo(1);
        assertThat(handled).hasSize(1);
    }

    @Test
    void ordersWithInvoiceInTheAskedState_areHandledInTheBatchOfThePass() {
        given(orders.findWithInvoiceIn(PaymentOrderState.CREATED, InvoiceState.READY,
                Limit.of(OrderingTestProperties.BATCH))).willReturn(List.of(first, second));

        int count = passes.eachWithInvoice(PaymentOrderState.CREATED, InvoiceState.READY, order -> {
            assertThat(MDC.get("correlationId")).isEqualTo(order.correlationId().toString());
            return handled.add(order.id());
        });

        assertThat(count).isEqualTo(2);
        assertThat(handled).containsExactly(PaymentOrders.ORDER, second.view().id());
    }

    @Test
    void ordersWithInvoice_areNotTouchedByStandby() {
        org.mockito.BDDMockito.willReturn(0).given(leadership).countAsLeader(any());

        int count = passes.eachWithInvoice(PaymentOrderState.CREATED, InvoiceState.READY, order -> handled.add(order.id()));

        assertThat(count).isZero();
        assertThat(handled).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(orders);
    }

    @Test
    void ordersOfAGivenSelection_areAskedWithTheBatchOfThePassAndHandledUntilOneAsksToStop() {
        List<Limit> asked = new ArrayList<>();

        int count = passes.eachOf(batch -> {
            asked.add(batch);
            return List.of(first, second);
        }, order -> {
            handled.add(order.id());
            return false;
        });

        assertThat(count).isEqualTo(1);
        assertThat(asked).containsExactly(Limit.of(OrderingTestProperties.BATCH));
        assertThat(handled).containsExactly(PaymentOrders.ORDER);
    }

    @Test
    void ordersOfAGivenSelection_areNotAskedByStandby() {
        org.mockito.BDDMockito.willReturn(0).given(leadership).countAsLeader(any());
        List<Limit> asked = new ArrayList<>();

        int count = passes.eachOf(batch -> {
            asked.add(batch);
            return List.of(first);
        }, order -> true);

        assertThat(count).isZero();
        assertThat(asked).isEmpty();
    }

    @Test
    void lostLeadership_isNotSwallowed() {
        org.mockito.BDDMockito.willAnswer(pass -> {
            try {
                return pass.<IntSupplier>getArgument(0).getAsInt();
            }
            catch (LeadershipLostException lost) {
                return -1;
            }
        }).given(leadership).countAsLeader(any());

        int count = passes.eachAsLeader(PaymentOrderState.AWAITING_CREATION, order -> {
            throw new LeadershipLostException("stand");
        });

        assertThat(count).isEqualTo(-1);
    }
}
