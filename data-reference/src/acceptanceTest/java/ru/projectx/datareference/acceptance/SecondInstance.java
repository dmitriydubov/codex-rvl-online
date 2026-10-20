package ru.projectx.datareference.acceptance;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import ru.projectx.datareference.DataReferenceApplication;

@Component
public class SecondInstance {

    private static final String ROLE = "second_instance";
    private static final String PASSWORD = "second-instance";
    private static final Duration DISCONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final String WITHOUT_ACCEPTANCE_STAND = "withoutAcceptanceStand";

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
        int apiPort = freePort();
        port = apiPort;
        SpringApplication application = new SpringApplicationBuilder(DataReferenceApplication.class)
                .initializers(context -> context.getBeanFactory()
                        .registerSingleton(WITHOUT_ACCEPTANCE_STAND, new WithoutAcceptanceStand()))
                .application();
        application.setListeners(application.getListeners().stream()
                .filter(listener -> !(listener instanceof LoggingApplicationListener))
                .toList());
        starting = CompletableFuture.supplyAsync(() -> application.run(arguments(apiPort)));
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
                    .ifPresent(ConfigurableApplicationContext::close);
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

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private String[] arguments(int apiPort) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", database.getJdbcUrl());
        properties.put("spring.datasource.username", ROLE);
        properties.put("spring.datasource.password", PASSWORD);
        properties.put("spring.rabbitmq.host", broker.getHost());
        properties.put("spring.rabbitmq.port", broker.getAmqpPort());
        properties.put("spring.rabbitmq.username", broker.getAdminUsername());
        properties.put("spring.rabbitmq.password", broker.getAdminPassword());
        properties.put("spring.rabbitmq.ssl.enabled", false);
        properties.put("spring.rabbitmq.listener.simple.auto-startup", true);
        properties.put("projectx.scheduling.enabled", false);
        properties.put("server.port", apiPort);
        properties.put("management.server.port", 0);
        return properties.entrySet().stream()
                .map(property -> "--" + property.getKey() + "=" + property.getValue())
                .toArray(String[]::new);
    }

    private static final class WithoutAcceptanceStand extends TypeExcludeFilter {

        private static final String STAND_PACKAGE = SecondInstance.class.getPackageName() + ".";

        @Override
        public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory) {
            return metadataReader.getClassMetadata().getClassName().startsWith(STAND_PACKAGE);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof WithoutAcceptanceStand;
        }

        @Override
        public int hashCode() {
            return WithoutAcceptanceStand.class.hashCode();
        }
    }
}
