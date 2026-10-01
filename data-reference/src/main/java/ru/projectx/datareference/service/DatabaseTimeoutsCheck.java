package ru.projectx.datareference.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.DatabaseSessionTimeouts;
import ru.projectx.datareference.repository.SessionSettingsRepository;

/**
 * Проверка действующих таймаутов сеанса PostgreSQL при старте (Ш41; решения владельца по PR #17
 * и п. 2 по PR #22). На соединении пула {@code lock_timeout}, {@code statement_timeout}
 * и {@code idle_in_transaction_session_timeout} должны совпасть с настройкой
 * ({@link DatabaseSessionTimeouts}), на соединении миграций — быть нулём.
 *
 * <p>Расхождение не роняет старт (решение владельца: сервис не должен падать из-за
 * инфраструктуры контура), а пишется WARN по каждому параметру — с именем соединения,
 * параметром и обоими значениями; адресов, учётных данных и ПДн в записи нет. Так видны обе
 * ловушки Ш4: пулер, молча выбросивший {@code options}, и таймаут на роли или базе, который
 * достаётся и миграциям.
 *
 * <p>{@link ApplicationRunner}: Spring Boot вызывает его после старта контекста и до готовности
 * принимать трафик — миграции к этому моменту прошли, их соединение открыто заново.
 */
@Component
public class DatabaseTimeoutsCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTimeoutsCheck.class);

    private static final String POOL = "пул приложения";
    private static final String MIGRATIONS = "миграции";

    private final DatabaseSessionTimeouts configured;
    private final SessionSettingsRepository sessionSettings;
    private final DataSource pool;
    private final SpringLiquibase liquibase;

    public DatabaseTimeoutsCheck(
            DatabaseSessionTimeouts configured,
            SessionSettingsRepository sessionSettings,
            DataSource pool,
            SpringLiquibase liquibase) {

        this.configured = configured;
        this.sessionSettings = sessionSettings;
        this.pool = pool;
        this.liquibase = liquibase;
    }

    @Override
    public void run(@Nullable ApplicationArguments arguments) {
        List<String> parameters = List.copyOf(configured.byParameter().keySet());
        Map<String, Duration> poolActual = sessionSettings.durations(pool, parameters);
        Map<String, Duration> migrationsActual = sessionSettings.durations(liquibase.getDataSource(), parameters);
        for (Map.Entry<String, String> setting : configured.byParameter().entrySet()) {
            checkPool(setting.getKey(), setting.getValue(), poolActual);
            warnIfDiffers(MIGRATIONS, setting.getKey(), Duration.ZERO, migrationsActual);
        }
    }

    private void checkPool(String parameter, String configuredValue, Map<String, Duration> actual) {
        Optional<Duration> expected = PostgresDurations.parse(configuredValue);
        if (expected.isEmpty()) {
            log.warn("проверка таймаутов PostgreSQL: настройка {} не разобрана, параметр не проверен", parameter);
            return;
        }
        warnIfDiffers(POOL, parameter, expected.get(), actual);
    }

    private static void warnIfDiffers(String connection, String parameter, Duration expected, Map<String, Duration> actual) {
        Optional<Duration> found = Optional.ofNullable(actual.get(parameter));
        if (found.filter(expected::equals).isEmpty()) {
            log.warn("таймаут сеанса PostgreSQL расходится с настройкой: соединение={}, параметр={}, "
                    + "ожидается={}, фактически={}", connection, parameter, expected,
                    found.map(Duration::toString).orElse("неизвестно"));
        }
    }
}
