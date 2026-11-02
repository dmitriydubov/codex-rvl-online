package ru.projectx.datareference.acceptance;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@Component
public class SecondInstance {

    private static final String ROLE = "second_instance";
    private static final String PASSWORD = "second-instance";
    private static final Duration DISCONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration START_TIMEOUT = Duration.ofMinutes(3);
    private static final Executor STARTER = command -> Thread.ofPlatform().name("second-instance-start").start(command);

    private static final String CREATE_ROLE = """
            do $$
            begin
              if not exists (select from pg_roles where rolname = '%1$s') then
                create role %1$s login password '%2$s' in role %3$s;
              end if;
            end
            $$
            """;
    private static final String ALLOW_LOGIN = "alter role " + ROLE + " login";
    private static final String FORBID_LOGIN = "alter role " + ROLE + " nologin";
    private static final String TERMINATE_SESSIONS = """
            select pg_terminate_backend(pid) from pg_stat_activity where usename = :role
            """;
    private static final String SESSIONS = "select count(*) from pg_stat_activity where usename = :role";

    private final PostgreSQLContainer database;
    private final RabbitMQContainer broker;
    private final JdbcClient jdbc;
    private @Nullable CompletableFuture<ConfigurableApplicationContext> starting;
    private CompletableFuture<ConfigurableApplicationContext> launched = new CompletableFuture<>();
    private int port;

    public SecondInstance(PostgreSQLContainer database, RabbitMQContainer broker) {
        this.database = database;
        this.broker = broker;
        this.jdbc = JdbcClient.create(new DriverManagerDataSource(
                database.getJdbcUrl(), database.getUsername(), database.getPassword()));
    }

    public void start() {
        begin();
        instance();
    }

    public void begin() {
        jdbc.sql(CREATE_ROLE.formatted(ROLE, PASSWORD, database.getUsername())).update();
        allowConnections();
        int apiPort = ServiceApplication.freePort();
        port = apiPort;
        SpringApplication application = ServiceApplication.withoutAcceptanceStand();
        CompletableFuture<ConfigurableApplicationContext> started =
                CompletableFuture.supplyAsync(() -> application.run(arguments(apiPort)), STARTER);
        launched = started;
        starting = started.copy().orTimeout(START_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
    }

    public int port() {
        return port;
    }

    public int managementPort() {
        return localPort("local.management.port");
    }

    public void cutOffFromDatabase() {
        jdbc.sql(FORBID_LOGIN).update();
        jdbc.sql(TERMINATE_SESSIONS).param("role", ROLE).query().listOfRows();
        await().atMost(DISCONNECT_TIMEOUT).until(() -> sessions() == 0);
    }

    public void allowConnections() {
        jdbc.sql(ALLOW_LOGIN).update();
    }

    public void stop() {
        Optional.ofNullable(starting).ifPresent(instance -> {
            allowConnections();
            Optional.ofNullable(instance.handle((context, failure) -> context).join())
                    .ifPresentOrElse(ConfigurableApplicationContext::close,
                            () -> launched.thenAccept(ConfigurableApplicationContext::close));
        });
        starting = null;
    }

    private long sessions() {
        return jdbc.sql(SESSIONS).param("role", ROLE).query(Long.class).single();
    }

    private int localPort(String property) {
        return instance().getEnvironment().getRequiredProperty(property, Integer.class);
    }

    private ConfigurableApplicationContext instance() {
        return Optional.ofNullable(starting)
                .orElseThrow(() -> new IllegalStateException("второй экземпляр не запущен"))
                .join();
    }

    private String[] arguments(int apiPort) {
        return ServiceApplication.arguments(ServiceApplication.properties(
                new ServiceApplication.DatabaseLogin(database.getJdbcUrl(), ROLE, PASSWORD), broker, apiPort));
    }
}
