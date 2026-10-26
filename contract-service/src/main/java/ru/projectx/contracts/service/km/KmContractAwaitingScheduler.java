package ru.projectx.contracts.service.km;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.service.ScheduledPasses;

@Component
public class KmContractAwaitingScheduler {

    private static final Logger log = LoggerFactory.getLogger(KmContractAwaitingScheduler.class);

    private final KmContractAwaiting awaiting;

    public KmContractAwaitingScheduler(KmContractAwaiting awaiting) {
        this.awaiting = awaiting;
    }

    @Scheduled(fixedDelayString = "${projectx.km-contract-submissions.awaiting-interval}")
    public void refresh() {
        ScheduledPasses.run(log, "пересчёта сводки договоров КМ, ждущих ответа 1С:УСК", awaiting::refresh);
    }
}
