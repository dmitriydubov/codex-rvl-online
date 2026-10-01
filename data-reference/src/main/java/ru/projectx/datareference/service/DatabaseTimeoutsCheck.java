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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.DatabaseSessionTimeouts;
import ru.projectx.datareference.config.ReferenceApiProperties;
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
 * <p>Проверка — наблюдение, а не условие старта: не прочиталось соединение (сбой базы, права
 * на {@code pg_settings}) — WARN с типом исключения, без его текста; Liquibase в контексте нет
 * (миграции в контуре идут отдельно) — WARN, что соединение миграций не проверено. Заодно —
 * WARN, если лимит REST-чтения не меньше включённого {@code lock_timeout}: тогда под чужой
 * блокировкой REST не отказывает первым ({@code lock_timeout} 0 — ожидание блокировок без предела,
 * и первым отказывает как раз лимит чтения).
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
    private final ReferenceApiProperties api;
    private final Connections connections;
    private final List<String> parameters;

    public DatabaseTimeoutsCheck(DatabaseSessionTimeouts configured, ReferenceApiProperties api, Connections connections) {
        this.configured = configured;
        this.api = api;
        this.connections = connections;
        this.parameters = List.copyOf(configured.byParameter().keySet());
    }

    @Override
    public void run(@Nullable ApplicationArguments arguments) {
        read(POOL, connections.pool()).ifPresent(this::checkPool);
        migrationsSource().flatMap(source -> read(MIGRATIONS, source)).ifPresent(this::checkMigrations);
        warnIfReadLimitNotBelowLockTimeout();
    }

    private void checkPool(Map<String, Duration> actual) {
        for (Map.Entry<String, String> setting : configured.byParameter().entrySet()) {
            checkPoolParameter(setting.getKey(), setting.getValue(), actual);
        }
    }

    private void checkMigrations(Map<String, Duration> actual) {
        for (String parameter : parameters) {
            warnIfDiffers(MIGRATIONS, parameter, Duration.ZERO, actual);
        }
    }

    private void checkPoolParameter(String parameter, String configuredValue, Map<String, Duration> actual) {
        Optional<Duration> expected = PostgresDurations.parse(configuredValue);
        if (expected.isEmpty()) {
            log.warn("проверка таймаутов PostgreSQL: настройка {} не разобрана, параметр не проверен", parameter);
            return;
        }
        warnIfDiffers(POOL, parameter, expected.get(), actual);
    }

    /** Граница системы: сбой чтения — WARN с типом исключения (текст несёт SQL), проверка идёт дальше. */
    private Optional<Map<String, Duration>> read(String connection, DataSource source) {
        try {
            return Optional.of(connections.sessionSettings().durations(source, parameters));
        }
        catch (DataAccessException failure) {
            log.warn("проверка таймаутов PostgreSQL: соединение={} не прочитано: {}",
                    connection, failure.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<DataSource> migrationsSource() {
        Optional<SpringLiquibase> liquibase = Optional.ofNullable(connections.liquibase().getIfAvailable());
        Optional<DataSource> source = liquibase.map(SpringLiquibase::getDataSource);
        if (source.isEmpty()) {
            log.warn("проверка таймаутов PostgreSQL: соединение миграций не проверено: {}",
                    liquibase.isPresent() ? "у Liquibase нет DataSource" : "Liquibase в контексте нет");
        }
        return source;
    }

    private void warnIfReadLimitNotBelowLockTimeout() {
        Duration readLimit = Duration.ofSeconds(api.readTimeoutSeconds());
        PostgresDurations.parse(configured.lockTimeout())
                .filter(lockTimeout -> lockTimeout.isPositive() && readLimit.compareTo(lockTimeout) >= 0)
                .ifPresent(lockTimeout -> log.warn("лимит REST-чтения {} не меньше lock_timeout {}: под чужой "
                        + "блокировкой REST не отказывает первым", readLimit, lockTimeout));
    }

    private static void warnIfDiffers(String connection, String parameter, Duration expected, Map<String, Duration> actual) {
        Optional<Duration> found = Optional.ofNullable(actual.get(parameter));
        if (found.filter(expected::equals).isEmpty()) {
            log.warn("таймаут сеанса PostgreSQL расходится с настройкой: соединение={}, параметр={}, "
                    + "ожидается={}, фактически={}", connection, parameter, expected,
                    found.map(Duration::toString).orElse("неизвестно"));
        }
    }

    /**
     * Откуда читать: чтение параметров сеанса, пул приложения и Liquibase — если он есть в контексте.
     *
     * @param liquibase провайдер, а не бин: при {@code spring.liquibase.enabled=false} бина нет
     */
    @Component
    public record Connections(
            SessionSettingsRepository sessionSettings, DataSource pool, ObjectProvider<SpringLiquibase> liquibase) {
    }
}
