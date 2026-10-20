package ru.projectx.datareference.acceptance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;

final class LateFirstReadDataSource extends DelegatingDataSource {

    private static final String PREPARE_STATEMENT = "prepareStatement";
    private static final String EXECUTE_QUERY = "executeQuery";

    private final AtomicBoolean firstResultOfNextReadLate = new AtomicBoolean();

    LateFirstReadDataSource(DataSource pool) {
        super(pool);
    }

    void delayFirstResultOfNextRead() {
        firstResultOfNextReadLate.set(true);
    }

    void restore() {
        firstResultOfNextReadLate.set(false);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return lateResults(Connection.class, super.getConnection(), this::statementWithLateResult);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return lateResults(Connection.class, super.getConnection(username, password), this::statementWithLateResult);
    }

    private Object statementWithLateResult(MethodInvocation invocation) throws Throwable {
        Object result = invocation.proceed();
        if (invocation.getMethod().getName().startsWith(PREPARE_STATEMENT)) {
            return lateResults(PreparedStatement.class, result, this::resultAfterReadDeadline);
        }
        return result;
    }

    private Object resultAfterReadDeadline(MethodInvocation invocation) throws Throwable {
        Object result = invocation.proceed();
        if (EXECUTE_QUERY.equals(invocation.getMethod().getName()) && isRestRead()
                && firstResultOfNextReadLate.compareAndSet(true, false)) {
            ReadDeadline.awaitExpiry();
        }
        return result;
    }

    private static boolean isRestRead() {
        return RequestContextHolder.getRequestAttributes() != null
                && TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    private static <T> T lateResults(Class<T> type, Object target, MethodInterceptor interceptor) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(interceptor);
        return type.cast(proxy.getProxy());
    }
}
