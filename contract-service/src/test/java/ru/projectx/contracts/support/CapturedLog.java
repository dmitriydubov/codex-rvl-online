package ru.projectx.contracts.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.slf4j.LoggerFactory;

public final class CapturedLog implements AutoCloseable {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private CapturedLog(Class<?> owner) {
        this.logger = (Logger) LoggerFactory.getLogger(owner);
        appender.start();
        logger.addAppender(appender);
    }

    public static CapturedLog of(Class<?> owner) {
        return new CapturedLog(owner);
    }

    public List<String> at(Level level) {
        return appender.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    public List<ILoggingEvent> events() {
        return appender.list;
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
    }
}
