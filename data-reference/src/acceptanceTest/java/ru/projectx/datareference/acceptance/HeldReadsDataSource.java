package ru.projectx.datareference.acceptance;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

final class HeldReadsDataSource extends DelegatingDataSource {

    private static final String INTERNAL_API = "/internal/v1";
    private static final String CONNECTION_REFUSED = "08001";

    private final AtomicInteger held = new AtomicInteger();
    private volatile @Nullable Hold hold;

    HeldReadsDataSource(DataSource pool) {
        super(pool);
    }

    void holdInternalApiReads() {
        held.set(0);
        hold = new Hold();
    }

    void release() {
        current().ifPresent(Hold::release);
        hold = null;
    }

    void releaseWithConnectionFailure() {
        current().ifPresent(Hold::releaseWithFailure);
        hold = null;
    }

    int held() {
        return held.get();
    }

    @Override
    public Connection getConnection() throws SQLException {
        awaitRelease();
        return super.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        awaitRelease();
        return super.getConnection(username, password);
    }

    private void awaitRelease() throws SQLException {
        Optional<Hold> current = current().filter(active -> isInternalApiRequest());
        if (current.isEmpty()) {
            return;
        }
        held.incrementAndGet();
        if (current.get().awaitAndFail()) {
            throw new SQLTransientConnectionException("стенд приёмки: соединение удержанному запросу не выдано",
                    CONNECTION_REFUSED);
        }
    }

    private Optional<Hold> current() {
        return Optional.ofNullable(hold);
    }

    private static boolean isInternalApiRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes http
                && http.getRequest().getRequestURI().startsWith(INTERNAL_API);
    }

    private static final class Hold {

        private final CountDownLatch gate = new CountDownLatch(1);
        private volatile boolean failing;

        void release() {
            gate.countDown();
        }

        void releaseWithFailure() {
            failing = true;
            gate.countDown();
        }

        boolean awaitAndFail() throws SQLException {
            try {
                gate.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new SQLTransientConnectionException("стенд приёмки: ожидание отпускания прервано", interrupted);
            }
            return failing;
        }
    }
}
