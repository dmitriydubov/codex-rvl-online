package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import io.cucumber.java.After;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.time.Duration;

public class NewDatabaseInstanceSteps {

    private static final int BEFORE_NEW_DATABASE_DROPPED = 25_000;
    private static final Duration AWAIT_MARGIN = Duration.ofSeconds(5);
    private static final String RELEASED_LOCK = "снят замок миграций";

    private final NewDatabase database;
    private final NewDatabaseInstances instances;
    private final ContractsService contracts;
    private final ServiceJournal journal;

    public NewDatabaseInstanceSteps(NewDatabase database, NewDatabaseInstances instances, ContractsService contracts,
            ServiceJournal journal) {
        this.database = database;
        this.instances = instances;
        this.contracts = contracts;
        this.journal = journal;
    }

    @Когда("{int} экземпляра Дата-референса одновременно запускаются на новой базе")
    public void startTogether(int count) {
        instances.startTogether(count, database);
    }

    @Когда("экземпляры на новой базе закончили старт")
    public void finishedStart() {
        instances.awaitStart();
    }

    @Тогда("через {int} секунд ни один экземпляр на новой базе не закончил старт и не отказал")
    public void noneFinishedFor(int seconds) {
        Duration waiting = Duration.ofSeconds(seconds);
        await().during(waiting).atMost(waiting.plus(AWAIT_MARGIN)).until(instances::noneFinished);
    }

    @Тогда("стартовали без отказа экземпляров на новой базе: {int}")
    public void startedWithoutFailure(int started) {
        assertThat(instances.startedWithoutFailure()).as("экземпляров, стартовавших без отказа").isEqualTo(started);
    }

    @Тогда("каждый экземпляр на новой базе отвечает на {string} {int}")
    public void eachInstanceAnswers(String path, int status) {
        assertThat(instances.startedPorts()).isNotEmpty()
                .allSatisfy(port -> assertThat(contracts.requestAt(port, path).status()).isEqualTo(status));
    }

    @Тогда("в журнале Дата-референса записей уровня {уровень} о снятом замке миграций экземпляра {string}, "
            + "взятом {string}, — {int}")
    public void releasedLockRecords(Level level, String lockedBy, String granted, int records) {
        assertThat(journal.mentioning(RELEASED_LOCK).stream()
                .filter(event -> event.getLevel() == level)
                .map(event -> event.getFormattedMessage())
                .filter(message -> message.contains(lockedBy) && message.contains(granted)))
                .as("записи о снятом замке миграций").hasSize(records);
    }

    @After(order = BEFORE_NEW_DATABASE_DROPPED)
    public void stopNewDatabaseInstances() {
        instances.stop();
    }
}
