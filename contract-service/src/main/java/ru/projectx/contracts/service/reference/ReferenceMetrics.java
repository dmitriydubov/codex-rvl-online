package ru.projectx.contracts.service.reference;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.exception.ReferenceKind;

@Component
public class ReferenceMetrics {

    static final String UNAVAILABLE = "contracts.reference.unavailable";
    static final String KIND = "kind";

    private final MeterRegistry registry;

    public ReferenceMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void countUnavailable(ReferenceKind kind) {
        Counter.builder(UNAVAILABLE)
                .description("обращения к справочному сервису, не получившие ответа после всех повторов")
                .tag(KIND, kind.name().toLowerCase(Locale.ROOT))
                .register(registry)
                .increment();
    }
}
