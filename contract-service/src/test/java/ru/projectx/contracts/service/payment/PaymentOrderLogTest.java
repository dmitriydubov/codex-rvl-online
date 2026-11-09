package ru.projectx.contracts.service.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ru.projectx.contracts.support.CapturedLog;
import ru.projectx.contracts.support.PaymentOrders;

class PaymentOrderLogTest {

    @Test
    void record_namesTheContractParticipantAndRequest() {
        try (CapturedLog log = CapturedLog.of(PaymentOrderLogTest.class)) {
            PaymentOrderLog.write(LoggerFactory.getLogger(PaymentOrderLogTest.class).atWarn(),
                    PaymentOrders.acceptedContract(), "причина");

            assertThat(log.at(Level.WARN)).containsExactly("заказ на оплату договора КМ " + PaymentOrders.CONTRACT
                    + " участника 9275471c-d2fe-484f-97a6-d6d7b1267b5e заявки 61a7982c-a0b4-4bbf-9527-60b40b752803: "
                    + "причина");
        }
    }

    @Test
    void correlationId_isInTheContextOnlyWhileTheWorkRuns() {
        boolean result = PaymentOrderLog.withCorrelation(PaymentOrders.CORRELATION,
                () -> PaymentOrders.CORRELATION.toString().equals(MDC.get("correlationId")));

        assertThat(result).isTrue();

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void correlationId_isRemovedEvenWhenTheWorkFails() {
        assertThatThrownBy(() -> PaymentOrderLog.withCorrelation(PaymentOrders.CORRELATION, () -> {
            throw new IllegalStateException("стенд");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get("correlationId")).isNull();
    }
}
