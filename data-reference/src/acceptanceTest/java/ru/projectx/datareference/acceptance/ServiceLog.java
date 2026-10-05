package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.ru.Тогда;
import java.util.Objects;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

/**
 * Журнал Дата-референса за время сценария. Сервис запущен в том же процессе, что и приёмка, поэтому его
 * записи собираются в память через корневой журнал Logback, подключённый перед сценарием и отключённый
 * после него. Запись об отказе делается до того, как ответ уходит клиенту, поэтому к шагу «Тогда» она
 * уже собрана.
 *
 * <p>Строку поиска, в которой может быть фамилия, сервис не повторяет ни в ответе, ни в журнале. Сценарии
 * пределов строят её из буквы «ж», поэтому три такие буквы подряд в теле ответа, в тексте записи, в её
 * контексте или в тексте исключения означают, что строка утекла.
 */
public class ServiceLog {

    private static final String SEARCH_STRING_MARK = "жжж";

    private final ListAppender<ILoggingEvent> events = new ListAppender<>();
    private final ContractsService contracts;

    public ServiceLog(ContractsService contracts) {
        this.contracts = contracts;
    }

    @Before
    public void startCollecting() {
        events.start();
        rootLogger().addAppender(events);
    }

    @After
    public void stopCollecting() {
        rootLogger().detachAppender(events);
        events.stop();
    }

    @Тогда("в журнале Дата-референса нет записей уровня ERROR")
    public void noErrors() {
        assertThat(events.list)
                .as("записи уровня ERROR в журнале")
                .noneMatch(event -> event.getLevel() == Level.ERROR);
    }

    @Тогда("строка query из запроса не попала ни в ответ, ни в журнал Дата-референса")
    public void searchStringNotRepeated() {
        assertThat(contracts.lastResponse().body().toString())
                .as("тело ответа")
                .doesNotContain(SEARCH_STRING_MARK);
        assertThat(events.list)
                .as("записи журнала со строкой поиска")
                .noneMatch(ServiceLog::mentionsSearchString);
    }

    private static boolean mentionsSearchString(ILoggingEvent event) {
        Stream<String> texts = Stream.of(
                Stream.of(event.getFormattedMessage()),
                event.getMDCPropertyMap().values().stream(),
                Stream.iterate(event.getThrowableProxy(), Objects::nonNull, IThrowableProxy::getCause)
                        .map(ServiceLog::message))
                .flatMap(text -> text);
        return texts.filter(Objects::nonNull).anyMatch(text -> text.contains(SEARCH_STRING_MARK));
    }

    private static @Nullable String message(IThrowableProxy throwable) {
        return throwable.getMessage();
    }

    private static Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }
}
