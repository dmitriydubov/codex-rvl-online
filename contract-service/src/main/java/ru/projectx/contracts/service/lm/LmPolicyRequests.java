package ru.projectx.contracts.service.lm;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.LmPolicyRequestProperties;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.service.RequestIds;
import ru.projectx.contracts.service.reference.ReferenceMetrics;

@Service
public class LmPolicyRequests {

    static final String REFERENCE_UNAVAILABLE = "справочный сервис недоступен, запрос отложен до следующего прохода";

    private static final Logger log = LoggerFactory.getLogger(LmPolicyRequests.class);

    private final LmPolicyRepository policies;
    private final LmPolicyReferences references;
    private final LmPolicyRequestRecording recording;
    private final ReferenceMetrics metrics;
    private final Limit batch;

    public LmPolicyRequests(LmPolicyRepository policies, LmPolicyReferences references,
            LmPolicyRequestRecording recording, ReferenceMetrics metrics, LmPolicyRequestProperties properties) {
        this.policies = policies;
        this.references = references;
        this.recording = recording;
        this.metrics = metrics;
        this.batch = Limit.of(properties.batchSize());
    }

    public int prepareDue() {
        List<LmPolicy> due = policies.findByStatusOrderByCreatedAtAscIdAsc(LmPolicyStatus.NEW, batch);
        int prepared = 0;
        for (LmPolicy policy : due) {
            if (!prepare(policy)) {
                break;
            }
            prepared++;
        }
        return prepared;
    }

    private boolean prepare(LmPolicy policy) {
        MDC.put(RequestIds.MDC_KEY, policy.id().toString());
        try {
            return prepareWithItsRequestId(policy);
        }
        finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }

    private boolean prepareWithItsRequestId(LmPolicy policy) {
        switch (references.resolve(policy.state())) {
            case RequestResolution.Resolved resolved -> recording.submit(policy.id(), resolved.identifiers());
            case RequestResolution.Refused refused -> recording.refuse(policy.id(), refused.error());
            case RequestResolution.Unavailable unavailable -> {
                postpone(policy, unavailable);
                return false;
            }
        }
        return true;
    }

    private void postpone(LmPolicy policy, RequestResolution.Unavailable unavailable) {
        log.warn("полис ЛМ {} участника {} заявки {}: {}", policy.id(), policy.participantId(),
                policy.state().request().individualRequestId(), REFERENCE_UNAVAILABLE);
        metrics.countUnavailable(unavailable.kind());
    }
}
