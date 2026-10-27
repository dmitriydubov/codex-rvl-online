package ru.projectx.datareference.support;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import ru.projectx.datareference.DataReferenceApplication;

public final class ServiceInstances {

    private ServiceInstances() {
    }

    public static ConfigurableApplicationContext startWithoutBroker(Environment testEnvironment, String databaseUrl,
            Map<String, Object> overrides) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.datasource.url", databaseUrl);
        properties.put("spring.datasource.username", testEnvironment.getRequiredProperty("spring.datasource.username"));
        properties.put("spring.datasource.password", testEnvironment.getRequiredProperty("spring.datasource.password"));
        properties.put("spring.autoconfigure.exclude",
                "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration");
        properties.put("spring.main.web-application-type", "none");
        properties.put("projectx.scheduling.enabled", false);
        properties.put("projectx.leadership.group", "instances-" + UUID.randomUUID());
        properties.putAll(overrides);
        String[] arguments = properties.entrySet().stream()
                .map(property -> "--" + property.getKey() + "=" + property.getValue())
                .toArray(String[]::new);
        return new SpringApplicationBuilder(DataReferenceApplication.class).run(arguments);
    }
}
