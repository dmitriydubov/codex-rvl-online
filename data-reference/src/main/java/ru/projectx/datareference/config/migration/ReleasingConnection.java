package ru.projectx.datareference.config.migration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;

final class ReleasingConnection implements InvocationHandler {

    private static final String CLOSE = "close";

    private final Connection target;

    private ReleasingConnection(Connection target) {
        this.target = target;
    }

    static Connection around(Connection target) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, new ReleasingConnection(target));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        if (CLOSE.equals(method.getName())) {
            releaseAndClose();
            return null;
        }
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failed) {
            throw failed.getCause();
        }
    }

    private void releaseAndClose() throws SQLException {
        if (target.isClosed()) {
            return;
        }
        try (Connection closing = target) {
            MigrationAdvisoryLock.release(closing);
        }
    }
}
