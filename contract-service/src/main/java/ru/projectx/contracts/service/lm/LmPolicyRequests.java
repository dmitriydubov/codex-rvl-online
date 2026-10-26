package ru.projectx.contracts.service.lm;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.LmPolicyRequestProperties;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.service.reference.IssuanceReferences;
import ru.projectx.contracts.service.reference.ReferenceMetrics;
import ru.projectx.contracts.service.usk.UskRequests;

@Service
public class LmPolicyRequests extends UskRequests<LmPolicy, LmPolicyStatus> {

    public LmPolicyRequests(LmPolicyRepository policies, LmPolicyRequestRecording recording,
            IssuanceReferences references, ReferenceMetrics metrics, LmPolicyRequestProperties properties) {
        super(LmPolicyKind.KIND, policies, recording, references, metrics, properties.batchSize(),
                LoggerFactory.getLogger(LmPolicyRequests.class));
    }
}
