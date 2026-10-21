package ru.projectx.datareference.service.warmup;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class LocalApi {

    private static final String SERVER_PORT = "local.server.port";
    private static final String LOOPBACK = "http://localhost:";

    private final Environment environment;

    public LocalApi(Environment environment) {
        this.environment = environment;
    }

    public Optional<LocalApiClient> connect(Duration connectTimeout) {
        return Optional.ofNullable(environment.getProperty(SERVER_PORT, Integer.class))
                .map(port -> new LocalApiClient(URI.create(LOOPBACK + port), connectTimeout));
    }
}
