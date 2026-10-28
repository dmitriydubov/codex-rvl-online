package ru.projectx.contracts.service.km;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.service.usk.awaiting.AwaitingGauges;
import ru.projectx.contracts.service.usk.awaiting.AwaitingMetrics;

@Component
public class KmContractAwaitingMetrics extends AwaitingMetrics {

    static final String OLDEST = "contracts.km.contracts.awaiting.oldest";
    static final String OVERDUE = "contracts.km.contracts.awaiting.overdue";

    public KmContractAwaitingMetrics(MeterRegistry registry) {
        super(registry, new AwaitingGauges(OLDEST, "сколько ждёт ответа 1С:УСК самый старый переданный договор КМ",
                OVERDUE, "договоры КМ, которые ждут ответа 1С:УСК дольше порога"));
    }
}
