package ru.projectx.contracts.service.lm;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.LmPolicyRequestProperties;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.reference.IssuanceReferences;
import ru.projectx.contracts.service.reference.ReferenceMetrics;
import ru.projectx.contracts.service.usk.SubmissionPrerequisites;
import ru.projectx.contracts.service.usk.UskPreparationMetrics;
import ru.projectx.contracts.service.usk.UskRequests;

@Service
public class LmPolicyRequests extends UskRequests<LmPolicy, LmPolicyStatus> {

    public LmPolicyRequests(LmPolicyRepository policies, LmPolicyRequestRecording recording,
            IssuanceReferences references, ReferenceMetrics metrics, UskPreparationMetrics preparationMetrics,
            LmPolicyRequestProperties properties, BackgroundLeadership leadership) {
        super(LmPolicyKind.KIND, policies, recording, SubmissionPrerequisites.none(), references, metrics,
                preparationMetrics, properties.batchSize(), leadership, LoggerFactory.getLogger(LmPolicyRequests.class));
    }
}
