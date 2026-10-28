package ru.projectx.contracts.service.lm.issue;

import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.service.inbox.parse.MessageBodies;
import ru.projectx.contracts.service.inbox.usk.UskResultParser;
import ru.projectx.contracts.service.lm.LmPolicyKind;
import ru.projectx.contracts.service.lm.LmPolicyTexts;
import ru.projectx.contracts.service.reference.IssuedPolicyLookup;
import ru.projectx.contracts.service.usk.result.UskResultHandler;

@Component
public class LmPolicyIssuedHandler extends UskResultHandler<LmPolicy, LmPolicyStatus> {

    public LmPolicyIssuedHandler(MessageBodies bodies, IssuedPolicyLookup lookup, IssuedPolicyRecording recording) {
        super(new UskResultParser(bodies, LmPolicyTexts.LAYOUT), LmPolicyKind.KIND, lookup, recording);
    }
}
