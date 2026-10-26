package ru.projectx.contracts.service.lm;

import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.entity.PersonOwner;
import ru.projectx.contracts.message.LmPolicyEvents;
import ru.projectx.contracts.service.IssueErrors;
import ru.projectx.contracts.service.usk.IssuanceKind;

final class LmPolicyKind {

    static final IssuanceKind<LmPolicy, LmPolicyStatus> KIND = new IssuanceKind<>(LmPolicyTexts.WORDING, "LM_POLICY",
            LmPolicyEvents.LM_POLICY_ISSUED, LmPolicyStatus.NEW, LmPolicyStatus.SUBMITTED,
            IssueErrors.lmPolicyRequestUndelivered(), PersonOwner::lmPolicy, LmPolicy::markIssued);

    private LmPolicyKind() {
    }
}
