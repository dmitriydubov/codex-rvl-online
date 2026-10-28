package ru.projectx.contracts.service.lm;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.repository.PrintedFormRepository;
import ru.projectx.contracts.service.status.RequestStatusPublication;
import ru.projectx.contracts.service.usk.UskResultRecording;

@Service
public class IssuedPolicyRecording extends UskResultRecording<LmPolicy, LmPolicyStatus> {

    public IssuedPolicyRecording(LmPolicyRepository policies, PrintedFormRepository forms,
            RequestStatusPublication statuses) {
        super(LmPolicyKind.KIND, policies, forms, statuses, LoggerFactory.getLogger(IssuedPolicyRecording.class));
    }
}
