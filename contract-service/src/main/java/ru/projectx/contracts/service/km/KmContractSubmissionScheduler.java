package ru.projectx.contracts.service.km;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.service.ScheduledPasses;

@Component
public class KmContractSubmissionScheduler {

    private static final Logger log = LoggerFactory.getLogger(KmContractSubmissionScheduler.class);

    private final KmContractSubmissions submissions;

    public KmContractSubmissionScheduler(KmContractSubmissions submissions) {
        this.submissions = submissions;
    }

    @Scheduled(fixedDelayString = "${projectx.km-contract-submissions.interval}")
    public void prepareDue() {
        ScheduledPasses.run(log, "подготовки передач договоров КМ в 1С:УСК", submissions::prepareDue);
    }
}
