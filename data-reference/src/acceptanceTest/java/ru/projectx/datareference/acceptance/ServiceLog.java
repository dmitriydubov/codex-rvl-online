package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.ru.Тогда;
import org.slf4j.LoggerFactory;

/**
 * Журнал Дата-референса за время сценария. Сервис запущен в том же процессе, что и приёмка, поэтому его
 * записи собираются в память через корневой журнал Logback, подключённый перед сценарием и отключённый
 * после него. Запись об отказе делается до того, как ответ уходит клиенту, поэтому к шагу «Тогда» она
 * уже собрана.
 */
public class ServiceLog {

    private final ListAppender<ILoggingEvent> events = new ListAppender<>();

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

    private static Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }
}
