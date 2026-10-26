package ru.projectx.contracts.service.lm;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.service.usk.AwaitingGauges;
import ru.projectx.contracts.service.usk.AwaitingMetrics;

@Component
public class LmPolicyAwaitingMetrics extends AwaitingMetrics {

    static final String OLDEST = "contracts.lm.policies.awaiting.oldest";
    static final String OVERDUE = "contracts.lm.policies.awaiting.overdue";

    public LmPolicyAwaitingMetrics(MeterRegistry registry) {
        super(registry, new AwaitingGauges(OLDEST, "сколько ждёт ответа 1С:УСК самый старый переданный полис ЛМ",
                OVERDUE, "полисы ЛМ, которые ждут ответа 1С:УСК дольше порога"));
    }
}
