package ru.projectx.datareference.acceptance;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Component
public class NewDatabase {

    private static final String DATABASE_IN_URL = "^(jdbc:postgresql://[^/]+/)[^?]*";
    private static final String CHANGE_SETS = """
            select id || ' ' || author || ' ' || filename from databasechangelog
            """;
    private static final String CHANGE_SET_LOG_EXISTS = "select to_regclass('databasechangelog') is not null";
    private static final String CHANGE_SET_COUNT = "select count(*) from databasechangelog";
    private static final String TIMES_EACH_CHANGE_SET_RECORDED = """
            select distinct count(*) from databasechangelog group by id, author, filename
            """;

    private final PostgreSQLContainer server;
    private final JdbcClient serviceDatabase;
    private @Nullable String name;

    public NewDatabase(PostgreSQLContainer server) {
        this.server = server;
        this.serviceDatabase = JdbcClient.create(new DriverManagerDataSource(
                server.getJdbcUrl(), server.getUsername(), server.getPassword()));
    }

    public void createEmpty() {
        String created = "new_database_" + UUID.randomUUID().toString().replace("-", "");
        serviceDatabase.sql("create database " + created).update();
        name = created;
    }

    public void createWithAllChangeSetsButLast() {
        createEmpty();
        PriorVersionMigrations.apply(url(), server.getUsername(), server.getPassword(), changeSetsHere().size() - 1);
    }

    public long changeSetCount() {
        boolean logExists = jdbc().sql(CHANGE_SET_LOG_EXISTS).query(Boolean.class).single();
        return logExists ? jdbc().sql(CHANGE_SET_COUNT).query(Long.class).single() : 0;
    }

    public List<Long> timesEachChangeSetRecorded() {
        return jdbc().sql(TIMES_EACH_CHANGE_SET_RECORDED).query(Long.class).list();
    }

    public Set<String> changeSets() {
        return Set.copyOf(jdbc().sql(CHANGE_SETS).query(String.class).list());
    }

    public Set<String> changeSetsHere() {
        return serviceDatabase.sql(CHANGE_SETS).query(String.class).list().stream().collect(Collectors.toSet());
    }

    public String url() {
        return server.getJdbcUrl().replaceFirst(DATABASE_IN_URL, "$1" + Optional.ofNullable(name)
                .orElseThrow(() -> new IllegalStateException("новая база не создана")));
    }

    public void drop() {
        Optional.ofNullable(name).ifPresent(created ->
                serviceDatabase.sql("drop database if exists " + created + " with (force)").update());
        name = null;
    }

    public JdbcClient jdbc() {
        return JdbcClient.create(new DriverManagerDataSource(url(), server.getUsername(), server.getPassword()));
    }

    public String username() {
        return server.getUsername();
    }

    public String password() {
        return server.getPassword();
    }
}
