package ru.projectx.datareference.service.rest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.ReferenceApiProperties;

@Component
public class RestReadAdmission {

    public static final String REJECTED = "rest.read.rejected";
    public static final String IN_FLIGHT = "rest.read.in.flight";

    private final int limit;
    private final Semaphore places;
    private final Counter rejected;

    public RestReadAdmission(ReferenceApiProperties properties, MeterRegistry registry) {
        this.limit = properties.maxConcurrentRequests();
        this.places = new Semaphore(limit);
        this.rejected = Counter.builder(REJECTED)
                .description("Запросы к /internal/v1, отклонённые по пределу одновременно обрабатываемых запросов")
                .register(registry);
        Gauge.builder(IN_FLIGHT, places, free -> inFlight(limit, free))
                .strongReference(true)
                .description("Запросы к /internal/v1 в обработке")
                .register(registry);
    }

    public boolean tryEnter() {
        if (places.tryAcquire()) {
            return true;
        }
        rejected.increment();
        return false;
    }

    public void leave() {
        places.release();
    }

    public int limit() {
        return limit;
    }

    private static int inFlight(int limit, Semaphore places) {
        return limit - places.availablePermits();
    }
}
