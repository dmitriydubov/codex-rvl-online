package ru.projectx.contracts.service.payment.order;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.config.payment.PaymentProperties;
import ru.projectx.contracts.support.CapturedLog;

class PaymentSwitchReportTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void disabledPayment_isWarnedAtStartAndFlagged() {
        try (CapturedLog log = CapturedLog.of(PaymentSwitchReport.class)) {
            new PaymentSwitchReport(new PaymentProperties(false), registry).report();

            assertThat(log.at(Level.WARN)).singleElement().asString().startsWith("оплата выключена");
            assertThat(log.at(Level.INFO)).isEmpty();
        }
        assertThat(flag()).isEqualTo(1);
    }

    @Test
    void enabledPayment_isReportedWithoutWarningAndNotFlagged() {
        try (CapturedLog log = CapturedLog.of(PaymentSwitchReport.class)) {
            new PaymentSwitchReport(new PaymentProperties(true), registry).report();

            assertThat(log.at(Level.WARN)).isEmpty();
            assertThat(log.at(Level.INFO)).hasSize(1);
        }
        assertThat(flag()).isZero();
    }

    private double flag() {
        return registry.get("contracts.payment.disabled").gauge().value();
    }
}
