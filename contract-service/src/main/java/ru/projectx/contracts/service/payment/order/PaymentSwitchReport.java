package ru.projectx.contracts.service.payment.order;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.payment.PaymentProperties;
import ru.projectx.contracts.service.FlagGauges;

@Component
public class PaymentSwitchReport {

    static final String DISABLED = "contracts.payment.disabled";

    private static final Logger log = LoggerFactory.getLogger(PaymentSwitchReport.class);

    private final boolean enabled;

    public PaymentSwitchReport(PaymentProperties properties, MeterRegistry registry) {
        this.enabled = properties.enabled();
        FlagGauges.register(registry, DISABLED, "оплата договоров КМ через сервис управления заказами выключена",
                () -> !enabled);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void report() {
        if (enabled) {
            log.info("оплата договоров КМ через сервис управления заказами включена");
            return;
        }
        log.warn("оплата выключена: заказы на оплату договоров КМ не создаются, сервис управления заказами, "
                + "сервер токенов и брокер маркетинга не подключены");
    }
}
