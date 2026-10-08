package ru.projectx.datareference.acceptance;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import io.cucumber.spring.ScenarioScope;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

/**
 * Записи журнала Дата-референса за время сценария. Сервис запущен в том же процессе, что и приёмка, поэтому его
 * записи собираются в память через корневой журнал Logback. Сбор включают и выключают хуки {@link ServiceLog}, а
 * читают записи шаги о журнале разных фич.
 */
@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class ServiceJournal {

    private final ListAppender<ILoggingEvent> events = new ListAppender<>();

    void start() {
        events.start();
        rootLogger().addAppender(events);
    }

    void stop() {
        rootLogger().detachAppender(events);
        events.stop();
    }

    List<ILoggingEvent> events() {
        return List.copyOf(events.list);
    }

    /** Записи, в тексте, контексте или тексте приложенного исключения которых встречается {@code text}. */
    List<ILoggingEvent> mentioning(String text) {
        return events().stream().filter(event -> texts(event).anyMatch(value -> value.contains(text))).toList();
    }

    private static Stream<String> texts(ILoggingEvent event) {
        return Stream.of(
                        Stream.of(event.getFormattedMessage()),
                        event.getMDCPropertyMap().values().stream(),
                        Stream.iterate(event.getThrowableProxy(), Objects::nonNull, IThrowableProxy::getCause)
                                .map(IThrowableProxy::getMessage))
                .flatMap(Function.identity())
                .filter(Objects::nonNull);
    }

    private static Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }
}
