package ru.projectx.datareference.acceptance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

final class PriorVersionMigrations {

    private static final String CHANGE_LOG = "db/changelog/db.changelog-master.yaml";
    private static final String ANALYTICS_ENABLED = "liquibase.analytics.enabled";
    private static final String ALL_CONTEXTS = "";

    private PriorVersionMigrations() {
    }

    static void apply(String url, String username, String password, int changeSets) {
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            Scope.child(Map.of(ANALYTICS_ENABLED, false), () -> {
                Database database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                new Liquibase(CHANGE_LOG, new ClassLoaderResourceAccessor(), database).update(changeSets, ALL_CONTEXTS);
            });
        } catch (Exception failed) {
            throw new IllegalStateException("стенд не накатил миграции прежней версии на новую базу", failed);
        }
    }
}
