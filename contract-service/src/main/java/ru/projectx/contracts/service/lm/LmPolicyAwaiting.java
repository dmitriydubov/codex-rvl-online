package ru.projectx.contracts.service.lm;

import java.time.Clock;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.config.LmPolicyRequestProperties;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.service.usk.UskAwaiting;

@Service
public class LmPolicyAwaiting extends UskAwaiting<LmPolicy, LmPolicyStatus> {

    public LmPolicyAwaiting(LmPolicyRepository policies, LmPolicyAwaitingMetrics metrics,
            LmPolicyRequestProperties properties, Clock clock) {
        super(LmPolicyKind.KIND, policies, metrics, properties.awaitingThreshold(), clock);
    }
}
