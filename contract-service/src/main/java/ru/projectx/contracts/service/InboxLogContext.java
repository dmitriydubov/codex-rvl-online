package ru.projectx.contracts.service;

import org.slf4j.MDC;
import ru.projectx.contracts.entity.InboxReceipt;

public final class InboxLogContext {

    public static final String MESSAGE_ID = "messageId";
    public static final String INDIVIDUAL_REQUEST_ID = "individualRequestId";

    private InboxLogContext() {
    }

    public interface Action<E extends Exception> {

        void run() throws E;
    }

    public static <E extends Exception> void run(InboxReceipt receipt, Action<E> action) throws E {
        MDC.put(MESSAGE_ID, receipt.messageId().toString());
        MDC.put(INDIVIDUAL_REQUEST_ID, receipt.correlationId().toString());
        MDC.put(RequestIds.MDC_KEY, receipt.messageId().toString());
        try {
            action.run();
        }
        finally {
            MDC.remove(MESSAGE_ID);
            MDC.remove(INDIVIDUAL_REQUEST_ID);
            MDC.remove(RequestIds.MDC_KEY);
        }
    }
}
