package ru.projectx.contracts.service.payment;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

final class PaymentErrorLog implements AutoCloseable {

    private static final String PAYMENT_LOGGERS = "ru.projectx.contracts.service.payment";

    private final Logger logger = (Logger) LoggerFactory.getLogger(PAYMENT_LOGGERS);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    PaymentErrorLog() {
        appender.start();
        logger.addAppender(appender);
    }

    List<String> errors() {
        return List.copyOf(appender.list).stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
    }
}
