package ru.projectx.datareference.config.cache;

import com.zaxxer.hikari.HikariDataSource;
import java.time.Duration;
import java.util.Properties;

final class ListenerConnectionSettings {

    static final String LISTENER_APPLICATION_NAME = "data-reference-cache-notifications";

    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private static final String APPLICATION_NAME = "ApplicationName";
    private static final String CONNECT_TIMEOUT = "connectTimeout";
    private static final String LOGIN_TIMEOUT = "loginTimeout";
    private static final long MIN_TIMEOUT_SECONDS = 1;

    private ListenerConnectionSettings() {
    }

    static Properties like(HikariDataSource application) {
        String timeoutSeconds = Long.toString(Math.max(MIN_TIMEOUT_SECONDS,
                Duration.ofMillis(application.getConnectionTimeout()).toSeconds()));
        Properties settings = new Properties();
        settings.putAll(application.getDataSourceProperties());
        settings.setProperty(USER, application.getUsername());
        settings.setProperty(PASSWORD, application.getPassword());
        settings.setProperty(APPLICATION_NAME, LISTENER_APPLICATION_NAME);
        settings.setProperty(CONNECT_TIMEOUT, timeoutSeconds);
        settings.setProperty(LOGIN_TIMEOUT, timeoutSeconds);
        return settings;
    }
}
