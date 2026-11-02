package ru.projectx.datareference.acceptance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import ru.projectx.datareference.DataReferenceApplication;

final class ServiceApplication {

    private static final String WITHOUT_ACCEPTANCE_STAND = "withoutAcceptanceStand";

    private ServiceApplication() {
    }

    static SpringApplication withoutAcceptanceStand() {
        SpringApplication application = new SpringApplicationBuilder(DataReferenceApplication.class)
                .initializers(context -> context.getBeanFactory()
                        .registerSingleton(WITHOUT_ACCEPTANCE_STAND, new WithoutAcceptanceStand()))
                .application();
        application.setListeners(application.getListeners().stream()
                .filter(listener -> !(listener instanceof LoggingApplicationListener))
                .toList());
        return application;
    }

    static Map<String, Object> properties(DatabaseLogin login, RabbitMQContainer broker, int apiPort) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", login.url());
        properties.put("spring.datasource.username", login.username());
        properties.put("spring.datasource.password", login.password());
        properties.put("spring.rabbitmq.host", broker.getHost());
        properties.put("spring.rabbitmq.port", broker.getAmqpPort());
        properties.put("spring.rabbitmq.username", broker.getAdminUsername());
        properties.put("spring.rabbitmq.password", broker.getAdminPassword());
        properties.put("spring.rabbitmq.ssl.enabled", false);
        properties.put("spring.rabbitmq.listener.simple.auto-startup", true);
        properties.put("projectx.scheduling.enabled", false);
        properties.put("server.port", apiPort);
        properties.put("management.server.port", 0);
        return properties;
    }

    static String[] arguments(Map<String, Object> properties) {
        return properties.entrySet().stream()
                .map(property -> "--" + property.getKey() + "=" + property.getValue())
                .toArray(String[]::new);
    }

    static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    record DatabaseLogin(String url, String username, String password) {
    }

    private static final class WithoutAcceptanceStand extends TypeExcludeFilter {

        private static final String STAND_PACKAGE = ServiceApplication.class.getPackageName() + ".";

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
