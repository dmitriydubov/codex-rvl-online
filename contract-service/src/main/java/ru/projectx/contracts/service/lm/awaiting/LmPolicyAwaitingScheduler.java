package ru.projectx.contracts.service.lm.awaiting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.service.ScheduledPasses;

@Component
public class LmPolicyAwaitingScheduler {

    private static final Logger log = LoggerFactory.getLogger(LmPolicyAwaitingScheduler.class);

    private final LmPolicyAwaiting awaiting;

    public LmPolicyAwaitingScheduler(LmPolicyAwaiting awaiting) {
        this.awaiting = awaiting;
    }

    @Scheduled(fixedDelayString = "${projectx.lm-policy-requests.awaiting-interval}")
    public void refresh() {
        ScheduledPasses.run(log, "пересчёта сводки полисов ЛМ, ждущих ответа 1С:УСК", awaiting::refresh);
    }
}
