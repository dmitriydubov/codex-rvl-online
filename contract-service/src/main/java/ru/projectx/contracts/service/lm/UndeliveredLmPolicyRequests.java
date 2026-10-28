package ru.projectx.contracts.service.lm;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.repository.issuance.LmPolicyRepository;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.service.usk.UndeliveredUskRequests;

@Component
public class UndeliveredLmPolicyRequests extends UndeliveredUskRequests<LmPolicy, LmPolicyStatus> {

    public UndeliveredLmPolicyRequests(LmPolicyRepository policies, RequestStatusPublication statuses) {
        super(LmPolicyKind.KIND, policies, statuses, LoggerFactory.getLogger(UndeliveredLmPolicyRequests.class));
    }
}
