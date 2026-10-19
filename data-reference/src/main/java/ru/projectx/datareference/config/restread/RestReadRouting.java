package ru.projectx.datareference.config.restread;

import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;

public final class RestReadRouting extends AbstractRoutingDataSource {

    enum Pool { APPLICATION, REST_READ }

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
