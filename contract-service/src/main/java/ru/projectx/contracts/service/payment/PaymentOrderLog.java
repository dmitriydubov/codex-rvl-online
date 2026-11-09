package ru.projectx.contracts.service.payment;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import ru.projectx.contracts.entity.contract.Contract;

public final class PaymentOrderLog {

    public static final String CORRELATION_ID = "correlationId";

    private static final String FORMAT = "заказ на оплату договора КМ {} участника {} заявки {}: {}";

    private PaymentOrderLog() {
    }

    public static void write(LoggingEventBuilder event, Contract contract, String reason) {
        event.log(FORMAT, contract.id(), contract.participantId(), contract.state().request().individualRequestId(),
                reason);
    }

    public static void within(UUID correlationId, Runnable work) {
        MDC.put(CORRELATION_ID, correlationId.toString());
        try {
            work.run();
        }
        finally {
            MDC.remove(CORRELATION_ID);
        }
    }

    public static boolean withCorrelation(UUID correlationId, BooleanSupplier work) {
        MDC.put(CORRELATION_ID, correlationId.toString());
        try {
            return work.getAsBoolean();
        }
        finally {
            MDC.remove(CORRELATION_ID);
        }
    }
}
