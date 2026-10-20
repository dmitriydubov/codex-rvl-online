package ru.projectx.datareference.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Level;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.time.Duration;
import java.util.Map;

public class StartupWarmupSteps {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
    private static final int READY = 200;
    private static final String WARMUP_END = "прогрев %s:";

    private final SecondInstance second;
    private final ContractsService contracts;
    private final ServiceJournal journal;
    private final DatabaseContents database;
    private Map<String, String> remembered = Map.of();

    public StartupWarmupSteps(SecondInstance second, ContractsService contracts, ServiceJournal journal,
            DatabaseContents database) {
        this.second = second;
        this.contracts = contracts;
        this.journal = journal;
        this.database = database;
    }

    @Когда("второй экземпляр Дата-референса запускается на той же базе")
    public void secondInstanceStarting() {
        second.begin();
    }

    @Когда("Kubernetes запрашивает у второго экземпляра {string}")
    public void probe(String path) {
        contracts.requestAt(second.port(), path);
    }

    @Когда("Kubernetes запрашивает у второго экземпляра {string}, пока он не ответит 200, но не дольше {int} секунд")
    public void probeUntilReady(String path, int seconds) {
        await().atMost(Duration.ofSeconds(seconds)).pollInterval(POLL_INTERVAL).ignoreExceptions()
                .until(() -> contracts.requestAt(second.port(), path).status() == READY);
    }

    @Дано("стенд запомнил данные в базе Дата-референса")
    public void rememberDatabase() {
        remembered = database.fingerprints();
    }

    @Тогда("данные в базе Дата-референса те же, что запомнил стенд")
    public void databaseUnchanged() {
        assertThat(remembered).as("стенд запомнил данные").isNotEmpty();
        assertThat(database.fingerprints()).as("отпечатки таблиц Дата-референса").isEqualTo(remembered);
    }

    @Тогда("в журнале Дата-референса есть запись уровня {уровень} о конце прогрева с исходом {string}")
    public void warmupEnded(Level level, String outcome) {
        String end = WARMUP_END.formatted(outcome);
        assertThat(journal.events())
                .as("записи о конце прогрева")
                .anyMatch(event -> event.getLevel() == level && event.getFormattedMessage().startsWith(end));
    }
}
