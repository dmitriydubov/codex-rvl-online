package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import ru.projectx.datareference.config.DatabaseSessionTimeouts;
import ru.projectx.datareference.config.RestReadPoolProperties;
import ru.projectx.datareference.repository.SessionSettingsRepository;
import ru.projectx.datareference.support.TestApiProperties;

/**
 * Проверка таймаутов при старте: у соединения пула — значения из настройки, у соединения
 * миграций — ноль. Расхождение — WARN с параметром и обоими значениями, без адресов и ПДн;
 * старт не прерывается.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class DatabaseTimeoutsCheckTest {

    private static final List<String> PARAMETERS =
            List.of("lock_timeout", "statement_timeout", "idle_in_transaction_session_timeout");
    private static final Map<String, Duration> CONFIGURED = Map.of(
            "lock_timeout", Duration.ofSeconds(5),
            "statement_timeout", Duration.ofSeconds(30),
            "idle_in_transaction_session_timeout", Duration.ofMinutes(1));
    /** Сетевой тайм-аут пула REST-чтений по умолчанию: больше лимита чтения (3 с). */
    private static final int REST_SOCKET_TIMEOUT_SECONDS = 5;
    private static final Map<String, Duration> DISABLED = Map.of(
            "lock_timeout", Duration.ZERO,
            "statement_timeout", Duration.ZERO,
            "idle_in_transaction_session_timeout", Duration.ZERO);

    @Mock
    private SessionSettingsRepository sessionSettings;

    @Mock
    private DataSource pool;

    @Mock
    private DataSource migrations;

    @Mock
    private SpringLiquibase liquibase;

    @Mock
    private ObjectProvider<SpringLiquibase> liquibaseProvider;

    private DatabaseTimeoutsCheck check;

    @BeforeEach
    void setUp() {
        check = check(new DatabaseSessionTimeouts("5s", "30s", "1min"));
    }

    @Test
    void matchingTimeouts_writeNoWarning(CapturedOutput output) {
        withMigrations();
        when(sessionSettings.durations(pool, PARAMETERS)).thenReturn(CONFIGURED);
        when(sessionSettings.durations(migrations, PARAMETERS)).thenReturn(DISABLED);

        check.run(null);

        assertThat(output).doesNotContain("WARN");
    }

    /** Пулер выбросил {@code options}: у сеанса пула умолчания сервера, то есть нули. */
    @Test
    void poolWithoutTimeouts_warnsPerParameter_withBothValues(CapturedOutput output) {
        withMigrations();
        when(sessionSettings.durations(pool, PARAMETERS)).thenReturn(DISABLED);
        when(sessionSettings.durations(migrations, PARAMETERS)).thenReturn(DISABLED);

        check.run(null);

        assertThat(output.getOut().lines().filter(line -> line.contains("WARN"))).hasSize(3);
        assertThat(output).contains(
                "соединение=пул приложения, параметр=lock_timeout, ожидается=PT5S, фактически=PT0S",
                "параметр=statement_timeout, ожидается=PT30S, фактически=PT0S",
                "параметр=idle_in_transaction_session_timeout, ожидается=PT1M, фактически=PT0S");
    }

    /** Таймаут на роли или базе достался и миграциям — долгая миграция оборвётся. */
    @Test
    void migrationsWithTimeout_warn(CapturedOutput output) {
        withMigrations();
        when(sessionSettings.durations(pool, PARAMETERS)).thenReturn(CONFIGURED);
        when(sessionSettings.durations(migrations, PARAMETERS)).thenReturn(Map.of(
                "lock_timeout", Duration.ZERO,
                "statement_timeout", Duration.ofSeconds(30),
                "idle_in_transaction_session_timeout", Duration.ZERO));

        check.run(null);

        assertThat(output).contains("соединение=миграции, параметр=statement_timeout, ожидается=PT0S, фактически=PT30S");
        assertThat(output.getOut().lines().filter(line -> line.contains("WARN"))).hasSize(1);
    }

    @Test
    void parameterMissingOnServer_warnsAsUnknown(CapturedOutput output) {
        withMigrations();
        when(sessionSettings.durations(pool, PARAMETERS)).thenReturn(Map.of(
                "lock_timeout", Duration.ofSeconds(5), "statement_timeout", Duration.ofSeconds(30)));
        when(sessionSettings.durations(migrations, PARAMETERS)).thenReturn(DISABLED);

        check.run(null);

        assertThat(output).contains("параметр=idle_in_transaction_session_timeout, ожидается=PT1M, фактически=неизвестно");
    }

    @Test
    void unparsedSetting_warnsThatPoolIsNotChecked(CapturedOutput output) {
        withMigrations();
        check = check(new DatabaseSessionTimeouts("1.5s", "30s", "1min"));
        when(sessionSettings.durations(pool, PARAMETERS)).thenReturn(CONFIGURED);
        when(sessionSettings.durations(migrations, PARAMETERS)).thenReturn(DISABLED);

        check.run(null);

        assertThat(output).contains("настройка lock_timeout не разобрана, параметр не проверен");
        assertThat(output.getOut().lines().filter(line -> line.contains("WARN"))).hasSize(1);
    }

    /** Проверка — наблюдение, а не условие старта: недоступная база или права на pg_settings — WARN, не отказ. */
    @Test
    void readFailure_warnsWithConnectionAndType_andChecksTheOtherConnection(CapturedOutput output) {
        withMigrations();
        when(sessionSettings.durations(pool, PARAMETERS))
                .thenThrow(new DataAccessResourceFailureException("Иванов 1980-05-17"));
        when(sessionSettings.durations(migrations, PARAMETERS)).thenReturn(DISABLED);

        check.run(null);

        assertThat(output).contains("соединение=пул приложения", "DataAccessResourceFailureException")
                .doesNotContain("Иванов");
    }

    /** Миграции в контуре могут идти отдельно ({@code spring.liquibase.enabled=false}) — проверять нечего. */
    @Test
    void withoutLiquibase_warnsThatMigrationsAreNotChecked(CapturedOutput output) {
        when(liquibaseProvider.getIfAvailable()).thenReturn(null);
        when(sessionSettings.durations(pool, PARAMETERS)).thenReturn(CONFIGURED);

        check.run(null);

        assertThat(output).contains("соединение миграций не проверено: Liquibase в контексте нет");
        assertThat(output.getOut().lines().filter(line -> line.contains("WARN"))).hasSize(1);
    }

    /** Лимит REST-чтения не меньше lock_timeout — под чужой блокировкой REST уже не отказывает первым. */
    @Test
    void readTimeoutNotBelowLockTimeout_warns(CapturedOutput output) {
        withMigrations();
        when(sessionSettings.durations(pool, PARAMETERS)).thenReturn(CONFIGURED);
        when(sessionSettings.durations(migrations, PARAMETERS)).thenReturn(DISABLED);
        check = check(new DatabaseSessionTimeouts("3s", "30s", "1min"));

        check.run(null);

        assertThat(output).contains("лимит REST-чтения PT3S не меньше lock_timeout PT3S");
    }

    @Test
    void lockTimeoutDisabled_doesNotWarnAboutReadLimit(CapturedOutput output) {
        withMigrations();
        when(sessionSettings.durations(pool, PARAMETERS)).thenReturn(CONFIGURED);
        when(sessionSettings.durations(migrations, PARAMETERS)).thenReturn(DISABLED);
        check = check(new DatabaseSessionTimeouts("0", "30s", "1min"));

        check.run(null);

        assertThat(output).doesNotContain("лимит REST-чтения");
    }

    /**
     * Сетевой тайм-аут пула REST-чтений не больше лимита чтения — он оборвёт медленное чтение раньше, чем его
     * отменит сама база, и вместе с ним соединение.
     */
    @Test
    void restSocketTimeoutNotAboveReadLimit_warns(CapturedOutput output) {
        withMigrations();
        when(sessionSettings.durations(pool, PARAMETERS)).thenReturn(CONFIGURED);
        when(sessionSettings.durations(migrations, PARAMETERS)).thenReturn(DISABLED);
        check = check(new DatabaseSessionTimeouts("5s", "30s", "1min"), 3);

        check.run(null);

        assertThat(output).contains("сетевой тайм-аут пула REST-чтений PT3S не больше лимита REST-чтения PT3S");
    }

    @Test
    void restSocketTimeoutAboveReadLimit_doesNotWarn(CapturedOutput output) {
        withMigrations();
        when(sessionSettings.durations(pool, PARAMETERS)).thenReturn(CONFIGURED);
        when(sessionSettings.durations(migrations, PARAMETERS)).thenReturn(DISABLED);
        check = check(new DatabaseSessionTimeouts("5s", "30s", "1min"), 4);

        check.run(null);

        assertThat(output).doesNotContain("WARN");
    }

    @Test
    void liquibaseWithoutDataSource_warnsSo(CapturedOutput output) {
        when(liquibaseProvider.getIfAvailable()).thenReturn(liquibase);
        when(liquibase.getDataSource()).thenReturn(null);
        when(sessionSettings.durations(pool, PARAMETERS)).thenReturn(CONFIGURED);

        check.run(null);

        assertThat(output).contains("соединение миграций не проверено: у Liquibase нет DataSource");
    }

    private void withMigrations() {
        when(liquibaseProvider.getIfAvailable()).thenReturn(liquibase);
        when(liquibase.getDataSource()).thenReturn(migrations);
    }

    private DatabaseTimeoutsCheck check(DatabaseSessionTimeouts timeouts) {
        return check(timeouts, REST_SOCKET_TIMEOUT_SECONDS);
    }

    private DatabaseTimeoutsCheck check(DatabaseSessionTimeouts timeouts, int restSocketTimeoutSeconds) {
        RestReadPoolProperties restReadPool =
                new RestReadPoolProperties(5, Duration.ofSeconds(2), restSocketTimeoutSeconds, 2);
        return new DatabaseTimeoutsCheck(timeouts, TestApiProperties.defaults(), restReadPool,
                new DatabaseTimeoutsCheck.Connections(sessionSettings, pool, liquibaseProvider));
    }
}
