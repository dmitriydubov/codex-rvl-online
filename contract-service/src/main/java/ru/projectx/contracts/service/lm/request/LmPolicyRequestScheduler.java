package ru.projectx.contracts.service.lm.request;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.service.ScheduledPasses;

@Component
public class LmPolicyRequestScheduler {

    private static final Logger log = LoggerFactory.getLogger(LmPolicyRequestScheduler.class);

    private final LmPolicyRequests requests;

    public LmPolicyRequestScheduler(LmPolicyRequests requests) {
        this.requests = requests;
    }

    @Scheduled(fixedDelayString = "${projectx.lm-policy-requests.interval}")
    public void prepareDue() {
        ScheduledPasses.run(log, "подготовки запросов на выпуск полисов ЛМ", requests::prepareDue);
    }
}
