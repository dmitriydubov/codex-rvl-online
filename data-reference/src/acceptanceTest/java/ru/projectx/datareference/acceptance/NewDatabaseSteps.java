package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.After;
import io.cucumber.java.ParameterType;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class NewDatabaseSteps {

    private static final int AFTER_NEW_DATABASE_INSTANCES_STOPPED = 24_000;
    private static final String HELD = "занят";

    private final NewDatabase database;
    private final NewDatabaseLock lock;

    public NewDatabaseSteps(NewDatabase database, NewDatabaseLock lock) {
        this.database = database;
        this.lock = lock;
    }

    @ParameterType(name = "состояние замка", value = "свободен|занят")
    public Boolean lockState(String state) {
        return HELD.equals(state);
    }

    @Дано("стенд создал новую пустую базу Дата-референса")
    public void emptyDatabase() {
        database.createEmpty();
    }

    @Дано("стенд создал новую базу Дата-референса с миграциями прежней версии сервиса — всеми changeSet'ами, кроме последнего")
    public void priorVersionDatabase() {
        database.createWithAllChangeSetsButLast();
    }

    @Дано("замок миграций новой базы занят другим экземпляром")
    public void lockHeldByLivingInstance() throws SQLException {
        lock.holdByLivingInstance();
    }

    @Дано("замок миграций новой базы остался от погибшего экземпляра {string}, взят {string}")
    public void lockLeftByDeadInstance(String lockedBy, String granted) {
        lock.leaveFromDeadInstance(lockedBy, LocalDateTime.parse(granted));
    }

    @Когда("другой экземпляр освободил замок миграций новой базы")
    public void livingInstanceReleasedLock() throws SQLException {
        lock.releaseByLivingInstance();
    }

    @Тогда("замок миграций новой базы {состояние замка}")
    public void lockIs(Boolean held) {
        assertThat(lock.held()).as("замок миграций новой базы занят").isEqualTo(held);
    }

    @Тогда("changeSet'ов в журнале миграций новой базы — {int}")
    public void changeSetCount(int expected) {
        assertThat(database.changeSetCount()).as("строк журнала миграций новой базы").isEqualTo(expected);
    }

    @Тогда("в журнале миграций новой базы каждый changeSet записан {int} раз")
    public void eachChangeSetRecorded(int times) {
        assertThat(database.timesEachChangeSetRecorded()).as("сколько раз записан каждый changeSet")
                .isNotEmpty().containsOnly((long) times);
    }

    @Тогда("changeSet'ы в журнале миграций новой базы те же, что в базе этого экземпляра")
    public void sameChangeSetsAsHere() {
        assertThat(database.changeSets()).isEqualTo(database.changeSetsHere());
    }

    @After(order = AFTER_NEW_DATABASE_INSTANCES_STOPPED)
    public void dropNewDatabase() throws SQLException {
        lock.closeLivingInstance();
        database.drop();
    }
}
