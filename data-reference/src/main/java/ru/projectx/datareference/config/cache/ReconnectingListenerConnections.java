package ru.projectx.datareference.config.cache;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.integration.jdbc.channel.PgConnectionSupplier;

public final class ReconnectingListenerConnections implements PgConnectionSupplier {

    private static final Logger log = LoggerFactory.getLogger(ReconnectingListenerConnections.class);

    private static final double DOUBLING = 2.0;
    private static final String NO_SQLSTATE = "нет";

    private final PgConnectionSupplier connect;
    private final RetryTemplate retries;
    private final AtomicBoolean connected = new AtomicBoolean();

    ReconnectingListenerConnections(PgConnectionSupplier connect, ReferenceCacheNotificationProperties properties) {
        this.connect = connect;
        this.retries = new RetryTemplate(retryPolicy(properties));
    }

    static RetryPolicy retryPolicy(ReferenceCacheNotificationProperties properties) {
        return RetryPolicy.builder()
                .maxRetries(properties.reconnectRetries())
                .delay(properties.reconnectDelay())
                .multiplier(DOUBLING)
                .maxDelay(properties.reconnectMaxDelay())
                .build();
    }

    public boolean connected() {
        return connected.get();
    }

    @Override
    public PgConnection get() throws SQLException {
        connected.set(false);
        try {
            PgConnection connection = retries.execute(this::attempt);
            connected.set(true);
            return connection;
        } catch (RetryException e) {
            throw new SQLException("нет соединения для оповещений о сбросе кэша после серии попыток", e);
        }
    }

    private PgConnection attempt() throws SQLException {
        try {
            PgConnection connection = connect.get();
            log.info("соединение для оповещений о сбросе кэша открыто, кэш справочников будет сброшен целиком");
            return connection;
        } catch (SQLException e) {
            log.warn("нет соединения для оповещений о сбросе кэша: {}, SQLSTATE {}",
                    e.getClass().getSimpleName(), Optional.ofNullable(e.getSQLState()).orElse(NO_SQLSTATE));
            throw e;
        }
    }
}
