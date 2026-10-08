package ru.projectx.datareference.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Счётчик сообщений 1С, отклонённых потому, что родитель не пришёл за срок ожидания, по потокам. Ряды заводятся
 * нулями заранее, чтобы оповещение видело первое же увеличение.
 */
@Component
public class ParentWaitMetrics {

    public static final String EXPIRED = "onec.inbound.parent.wait.expired";

    private final Map<OneCStream, Counter> expired;

    public ParentWaitMetrics(MeterRegistry registry) {
        this.expired = Stream.of(OneCStream.values())
                .collect(Collectors.toUnmodifiableMap(Function.identity(), stream -> Counter.builder(EXPIRED)
                        .description("Сообщения 1С, отклонённые потому, что родитель не пришёл за срок ожидания")
                        .tag("stream", stream.name())
                        .register(registry)));
    }

    /** Вызывается после фиксации отказа. */
    public void expired(OneCStream stream) {
        expired.get(stream).increment();
    }
}
