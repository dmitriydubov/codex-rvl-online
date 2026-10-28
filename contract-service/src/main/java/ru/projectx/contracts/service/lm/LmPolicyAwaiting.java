package ru.projectx.contracts.service.lm;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import ru.projectx.contracts.config.usk.LmPolicyRequestProperties;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.repository.issuance.LmPolicyRepository;
import ru.projectx.contracts.service.leadership.BackgroundLeadership;
import ru.projectx.contracts.service.usk.UskAwaiting;

@Service
public class LmPolicyAwaiting extends UskAwaiting<LmPolicy, LmPolicyStatus> {

    public LmPolicyAwaiting(LmPolicyRepository policies, LmPolicyAwaitingMetrics metrics,
            LmPolicyRequestProperties properties, Clock clock, BackgroundLeadership leadership,
            PlatformTransactionManager transactions) {
        super(LmPolicyKind.KIND, policies, metrics, properties.awaitingThreshold(), clock, leadership, transactions);
    }
}
