package ru.projectx.datareference.config;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Выбор пула для очередного соединения: REST-чтения — в свой пул, всё остальное — в пул приложения.
 *
 * <p>REST-чтение здесь — транзакция «только чтение», начатая при обработке HTTP-запроса. Одного признака «только
 * чтение» мало: Spring Data открывает такую транзакцию на каждый вызов репозитория вне транзакции, и так читают
 * и разбор, и задачи по расписанию. Их чтение не должно попасть в пул с коротким сетевым тайм-аутом. Одного
 * HTTP-запроса тоже мало: проверка здоровья базы приходит запросом к актуатору, но без транзакции, и она должна
 * видеть пул приложения.
 *
 * <p>Признак транзакции Spring выставляет уже после того, как Hibernate взял соединение, поэтому решение
 * откладывается до первого оператора: эту маршрутизацию оборачивает {@link RestReadDataSource}.
 */
public final class RestReadRouting extends AbstractRoutingDataSource {

    /** Пулы, между которыми выбирает маршрутизация. */
    enum Pool { APPLICATION, REST_READ }

    /**
     * Маршрутизация между двумя пулами.
     *
     * @param application пул приёма, разбора, задач по расписанию и проверки здоровья
     * @param restRead    пул REST-чтений
     */
    public RestReadRouting(DataSource application, DataSource restRead) {
        setTargetDataSources(Map.of(Pool.APPLICATION, application, Pool.REST_READ, restRead));
        afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return isRestRead() ? Pool.REST_READ : Pool.APPLICATION;
    }

    private static boolean isRestRead() {
        return RequestContextHolder.getRequestAttributes() != null
                && TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }
}
