package ru.projectx.datareference.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.listener.ConsumeOkEvent;

class OneCConsumerOutageJournalTest {

    private static final String POLICIES = "q-policies";
    private static final String MKB10 = "q-mkb10";
    private static final String QUEUE_MISSING = "очереди нет на брокере";

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final OneCConsumerOutageJournal journal = new OneCConsumerOutageJournal();

    @BeforeEach
    void captureLog() {
        logged.start();
        journalLog().setLevel(Level.DEBUG);
        journalLog().addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        journalLog().detachAppender(logged);
        journalLog().setLevel(null);
        logged.stop();
    }

    @Test
    void firstFailureOfAQueue_isAnErrorNamingQueueAndReason() {
        journal.startFailed(POLICIES, QUEUE_MISSING);

        assertThat(errors()).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).isEqualTo("потребитель очереди q-policies не запущен: " + QUEUE_MISSING);
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void repeatedFailuresOfTheSameQueue_addNoErrors() {
        journal.startFailed(POLICIES, QUEUE_MISSING);
        journal.startFailed(POLICIES, QUEUE_MISSING);
        journal.startFailed(POLICIES, "AmqpIllegalStateException");

        assertThat(errors()).hasSize(1);
        assertThat(logged.list).filteredOn(event -> event.getLevel() == Level.DEBUG)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        "потребитель очереди q-policies по-прежнему не запущен: " + QUEUE_MISSING,
                        "потребитель очереди q-policies по-прежнему не запущен: AmqpIllegalStateException");
    }

    @Test
    void failuresOfDifferentQueues_areReportedEach() {
        journal.startFailed(POLICIES, QUEUE_MISSING);
        journal.startFailed(MKB10, QUEUE_MISSING);

        assertThat(errors()).extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains(POLICIES))
                .anySatisfy(message -> assertThat(message).contains(MKB10))
                .hasSize(2);
    }

    @Test
    void subscriptionAfterAReportedFailure_isOneInfoNamingTheQueue() {
        journal.startFailed(POLICIES, QUEUE_MISSING);

        journal.consumeOk(subscribed(POLICIES));
        journal.consumeOk(subscribed(POLICIES));

        assertThat(infos()).singleElement().satisfies(event -> assertThat(event.getFormattedMessage())
                .isEqualTo("потребитель очереди q-policies запущен: очередь снова на брокере"));
    }

    @Test
    void subscriptionWithoutAReportedFailure_writesNothing() {
        journal.consumeOk(subscribed(POLICIES));

        assertThat(logged.list).isEmpty();
    }

    @Test
    void failureAfterRecovery_isReportedAgain() {
        journal.startFailed(POLICIES, QUEUE_MISSING);
        journal.consumeOk(subscribed(POLICIES));

        journal.startFailed(POLICIES, QUEUE_MISSING);

        assertThat(errors()).hasSize(2);
    }

    @Test
    void recoveryOfOneQueue_leavesTheOtherOutageOpen() {
        journal.startFailed(POLICIES, QUEUE_MISSING);
        journal.startFailed(MKB10, QUEUE_MISSING);

        journal.consumeOk(subscribed(POLICIES));
        journal.startFailed(MKB10, QUEUE_MISSING);

        assertThat(infos()).singleElement()
                .satisfies(event -> assertThat(event.getFormattedMessage()).contains(POLICIES));
        assertThat(errors()).hasSize(2);
    }

    private static ConsumeOkEvent subscribed(String queue) {
        return new ConsumeOkEvent(new Object(), queue, "amq.ctag-" + queue);
    }

    private List<ILoggingEvent> errors() {
        return logged.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }

    private List<ILoggingEvent> infos() {
        return logged.list.stream().filter(event -> event.getLevel() == Level.INFO).toList();
    }

    private static Logger journalLog() {
        return (Logger) LoggerFactory.getLogger(OneCConsumerOutageJournal.class);
    }
}
