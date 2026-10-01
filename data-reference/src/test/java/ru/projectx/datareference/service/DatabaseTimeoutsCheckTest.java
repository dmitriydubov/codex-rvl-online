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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import ru.projectx.datareference.config.DatabaseSessionTimeouts;
import ru.projectx.datareference.repository.SessionSettingsRepository;

/**
 * Проверка таймаутов при старте (Ш41): у соединения пула — значения из настройки, у соединения
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

    private DatabaseTimeoutsCheck check;

    @BeforeEach
    void setUp() {
        when(liquibase.getDataSource()).thenReturn(migrations);
        check = new DatabaseTimeoutsCheck(
                new DatabaseSessionTimeouts("5s", "30s", "1min"), sessionSettings, pool, liquibase);
    }

    @Test
    void matchingTimeouts_writeNoWarning(CapturedOutput output) {
        when(sessionSettings.durations(pool, PARAMETERS)).thenReturn(CONFIGURED);
        when(sessionSettings.durations(migrations, PARAMETERS)).thenReturn(DISABLED);

        check.run(null);

        assertThat(output).doesNotContain("WARN");
    }

    /** Пулер выбросил {@code options}: у сеанса пула умолчания сервера, то есть нули. */
    @Test
    void poolWithoutTimeouts_warnsPerParameter_withBothValues(CapturedOutput output) {
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
        when(sessionSettings.durations(pool, PARAMETERS)).thenReturn(Map.of(
                "lock_timeout", Duration.ofSeconds(5), "statement_timeout", Duration.ofSeconds(30)));
        when(sessionSettings.durations(migrations, PARAMETERS)).thenReturn(DISABLED);

        check.run(null);

        assertThat(output).contains("параметр=idle_in_transaction_session_timeout, ожидается=PT1M, фактически=неизвестно");
    }

    @Test
    void unparsedSetting_warnsThatPoolIsNotChecked(CapturedOutput output) {
        check = new DatabaseTimeoutsCheck(
                new DatabaseSessionTimeouts("1.5s", "30s", "1min"), sessionSettings, pool, liquibase);
        when(sessionSettings.durations(pool, PARAMETERS)).thenReturn(CONFIGURED);
        when(sessionSettings.durations(migrations, PARAMETERS)).thenReturn(DISABLED);

        check.run(null);

        assertThat(output).contains("настройка lock_timeout не разобрана, параметр не проверен");
        assertThat(output.getOut().lines().filter(line -> line.contains("WARN"))).hasSize(1);
    }
}
