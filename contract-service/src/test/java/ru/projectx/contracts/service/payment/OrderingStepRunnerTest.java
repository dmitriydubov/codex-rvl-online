package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.exception.OrderingOperation;
import ru.projectx.contracts.exception.OrderingOrderFailureException;
import ru.projectx.contracts.exception.OrderingRejectedException;
import ru.projectx.contracts.exception.OrderingUnavailableException;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.OrderingTestProperties;
import ru.projectx.contracts.support.PaymentOrders;

class OrderingStepRunnerTest {

    private final Ordering ordering = mock();
    private final OrderingSession session = mock();
    private final OrderingStepRunner runner =
            new OrderingStepRunner(ordering, new OrderingAttempts(OrderingTestProperties.attempts(1)));
    private final List<String> succeeded = new ArrayList<>();
    private final List<String> rejected = new ArrayList<>();
    private final CapturedLog log = CapturedLog.of(OrderingStepRunner.class);

    @AfterEach
    void releaseLog() {
        log.close();
    }

    private OrderingStep<String> step(RuntimeException failure) {
        return new OrderingStep<>("не создан", any -> {
            throw failure;
        }, succeeded::add, rejected::add);
    }

    @Test
    void success_isHandedToTheStepAndThePassGoesOn() {
        given(ordering.session(PaymentOrders.CORRELATION)).willReturn(session);

        boolean goOn = runner.run(PaymentOrders.awaiting().view(),
                new OrderingStep<>("не создан", opened -> opened == session ? "o-1" : "чужая сессия", succeeded::add,
                        rejected::add));

        assertThat(goOn).isTrue();
        assertThat(succeeded).containsExactly("o-1");
        assertThat(rejected).isEmpty();
    }

    @Test
    void rejection_isHandedToTheStepWithItsCodeAndThePassGoesOn() {
        boolean goOn = runner.run(PaymentOrders.awaiting().view(),
                step(new OrderingRejectedException(OrderingOperation.CREATE_ORDER, "C")));

        assertThat(goOn).isTrue();
        assertThat(rejected).containsExactly("C");
        assertThat(succeeded).isEmpty();
    }

    @Test
    void unavailableService_stopsThePass() {
        boolean goOn = runner.run(PaymentOrders.awaiting().view(),
                step(new OrderingUnavailableException(OrderingOperation.CREATE_ORDER, "HTTP 503")));

        assertThat(goOn).isFalse();
        assertThat(log.at(Level.WARN)).containsExactly("заказ на оплату договора КМ " + PaymentOrders.CONTRACT
                + " не создан: сервис управления заказами недоступен: create_order, HTTP 503, проход остановлен до"
                + " следующего раза");
    }

    @Test
    void failureOfThisOrderOnly_leavesItForTheNextPassAndGoesOn() {
        boolean goOn = runner.run(PaymentOrders.awaiting().view(),
                step(new OrderingOrderFailureException(OrderingOperation.FIND_ORDERS, "поиск заказов отклонён, код C")));

        assertThat(goOn).isTrue();
        assertThat(succeeded).isEmpty();
        assertThat(rejected).isEmpty();
        assertThat(log.at(Level.WARN)).containsExactly("заказ на оплату договора КМ " + PaymentOrders.CONTRACT
                + " не создан: сервис управления заказами не обработал заказ: find_orders, поиск заказов отклонён,"
                + " код C, заказ ждёт следующего прохода");
    }
}
