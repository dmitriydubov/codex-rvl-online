package ru.projectx.contracts.service.lm;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.service.IssueErrors;
import ru.projectx.contracts.service.outbox.RejectedOutboxMessages;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;

@Component
public class UndeliveredLmPolicyRequests implements RejectedOutboxMessages {

    private static final Logger log = LoggerFactory.getLogger(UndeliveredLmPolicyRequests.class);

    private final LmPolicyRepository policies;
    private final RequestStatusPublication statuses;

    public UndeliveredLmPolicyRequests(LmPolicyRepository policies, RequestStatusPublication statuses) {
        this.policies = policies;
        this.statuses = statuses;
    }

    @Override
    public String aggregateType() {
        return LmPolicyRequestRecording.AGGREGATE_TYPE;
    }

    @Override
    public void rejected(UUID policyId, Instant now) {
        LmPolicy policy = policies.findById(policyId)
                .orElseThrow(() -> new IllegalStateException("полиса ЛМ " + policyId + " нет"));
        if (!policy.submitted()) {
            log.warn("запрос на выпуск полиса ЛМ {} не доставлен, полис уже в статусе {} и не меняется",
                    policyId, policy.status());
            return;
        }
        IssuanceSnapshot before = statuses.watch(policy.state().request().individualRequestId());
        policy.markError(IssueErrors.lmPolicyRequestUndelivered(), now);
        statuses.afterRecordChange(before, now);
        log.warn("запрос на выпуск полиса ЛМ {} не доставлен, полис переведён в ERROR", policyId);
    }
}
