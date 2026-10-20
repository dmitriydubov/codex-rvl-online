package ru.projectx.datareference.service.cache;

import java.sql.SQLException;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.integration.jdbc.channel.PgConnectionSupplier;
import ru.projectx.datareference.config.cache.ReferenceCacheNotificationProperties;
import ru.projectx.datareference.service.database.SqlStates;

final class ReconnectingListenerConnections implements PgConnectionSupplier {

    private static final Logger log = LoggerFactory.getLogger(ReconnectingListenerConnections.class);

    private static final double DOUBLING = 2.0;

    private final PgConnectionSupplier connect;
    private final RetryTemplate retries;

    ReconnectingListenerConnections(PgConnectionSupplier connect, ReferenceCacheNotificationProperties properties) {
        this.connect = connect;
        this.retries = new RetryTemplate(RetryPolicy.builder()
                .maxRetries(Long.MAX_VALUE)
                .delay(properties.reconnectDelay())
                .multiplier(DOUBLING)
                .maxDelay(properties.reconnectMaxDelay())
                .build());
    }

    @Override
    public PgConnection get() throws SQLException {
        try {
            return retries.execute(this::attempt);
        } catch (RetryException e) {
            throw new SQLException("подключение для оповещений о сбросе кэша прервано остановкой", e);
        }
    }

    private PgConnection attempt() throws SQLException {
        try {
            PgConnection connection = connect.get();
            log.info("соединение для оповещений о сбросе кэша открыто, кэш справочников будет сброшен целиком");
            return connection;
        } catch (SQLException e) {
            log.warn("нет соединения для оповещений о сбросе кэша: {}, SQLSTATE {}",
                    e.getClass().getSimpleName(), SqlStates.deepestOrNone(e));
            throw e;
        }
    }
}
