package ru.projectx.datareference.acceptance;

import static org.awaitility.Awaitility.await;

import com.zaxxer.hikari.HikariDataSource;
import io.cucumber.java.After;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import ru.projectx.datareference.config.LeadershipProperties;
import ru.projectx.datareference.config.restread.RestReadDataSource;
import ru.projectx.datareference.service.leadership.BackgroundLeadership;

public class ApplicationPoolSteps {

    private static final int AFTER_DATABASE_RESTORED_BEFORE_LOCK_RELEASE = 50;
    private static final Duration MARGIN = Duration.ofSeconds(2);

    private final HikariDataSource applicationPool;
    private final BackgroundLeadership leadership;
    private final LeadershipProperties leadershipSettings;
    private final List<Connection> held = new ArrayList<>();

    public ApplicationPoolSteps(DataSource dataSource, BackgroundLeadership leadership,
            LeadershipProperties leadershipSettings) throws SQLException {

        this.applicationPool = dataSource.unwrap(RestReadDataSource.class).applicationPool();
        this.leadership = leadership;
        this.leadershipSettings = leadershipSettings;
    }

    @Дано("все соединения основного пула Дата-референса заняты")
    public void allConnectionsTaken() throws SQLException {
        for (int taken = 0; taken < applicationPool.getMaximumPoolSize(); taken++) {
            held.add(applicationPool.getConnection());
        }
    }

    @Когда("основной пул Дата-референса занят дольше ожидания соединения и шага продления роли")
    public void poolBusyLongerThanConnectionWaitAndRenewal() {
        Duration longerThanConnectionWaitAndRenewal = Duration.ofMillis(applicationPool.getConnectionTimeout())
                .plus(leadershipSettings.heartbeat()).plus(MARGIN);

        await().during(longerThanConnectionWaitAndRenewal).atMost(longerThanConnectionWaitAndRenewal.plus(MARGIN))
                .until(leadership::leader);
    }

    @Когда("соединения основного пула Дата-референса освобождены")
    public void connectionsReleased() throws SQLException {
        release();
    }

    @After(order = AFTER_DATABASE_RESTORED_BEFORE_LOCK_RELEASE)
    public void releaseHeldConnections() throws SQLException {
        release();
    }

    private void release() throws SQLException {
        for (Connection connection : held) {
            connection.close();
        }
        held.clear();
    }
}
