package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import io.cucumber.spring.ScenarioScope;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
public class ServiceJournal {

    private static final Duration CALL_LOG_PATIENCE = Duration.ofSeconds(2);
    private static final Duration CALL_LOG_POLL = Duration.ofMillis(10);

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
        synchronized (events) {
            return List.copyOf(events.list);
        }
    }

    void assertSyncCallLogged(String method, String requestId, int status) {
        Awaitility.await().atMost(CALL_LOG_PATIENCE).pollInterval(CALL_LOG_POLL).untilAsserted(() ->
                assertThat(events()).as("записи журнала о вызове %s с requestId %s", method, requestId)
                        .filteredOn(event -> requestId.equals(event.getMDCPropertyMap().get("requestId")))
                        .extracting(ILoggingEvent::getFormattedMessage)
                        .anyMatch(message -> message.startsWith("синхронный вызов " + method + " ")
                                && message.endsWith(" завершён с кодом " + status)));
    }

    void assertPreparationFailure(String recordPrefix, String reason, String sqlState) {
        List<ILoggingEvent> matching = events().stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .filter(event -> event.getFormattedMessage().startsWith(recordPrefix + " " + reason + ", тип отказа "))
                .toList();
        assertThat(matching).as("записи ERROR «%s %s»", recordPrefix, reason).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).endsWith(", SQLSTATE " + sqlState);
            assertThat(event.getThrowableProxy()).as("приложенное исключение").isNull();
        });
    }

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
