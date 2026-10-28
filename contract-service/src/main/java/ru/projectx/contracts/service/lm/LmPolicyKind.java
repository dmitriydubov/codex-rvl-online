package ru.projectx.contracts.service.lm;

import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyStatus;
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.message.LmPolicyEvents;
import ru.projectx.contracts.service.IssueErrors;
import ru.projectx.contracts.service.usk.IssuanceKind;

final class LmPolicyKind {

    static final IssuanceKind<LmPolicy, LmPolicyStatus> KIND = new IssuanceKind<>(LmPolicyTexts.WORDING, "LM_POLICY",
            LmPolicyEvents.LM_POLICY_REQUESTED, LmPolicyEvents.LM_POLICY_ISSUED, LmPolicyStatus.NEW,
            LmPolicyStatus.SUBMITTED, IssueErrors.lmPolicyRequest(), PersonOwner::lmPolicy, LmPolicy::markIssued);

    private LmPolicyKind() {
    }
}
