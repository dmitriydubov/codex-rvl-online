package ru.projectx.contracts.service.lm;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.contracts.entity.IssueError;
import ru.projectx.contracts.entity.IssuedPolicy;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyStatus;
import ru.projectx.contracts.entity.PrintedForm;
import ru.projectx.contracts.entity.UskDocument;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.repository.LmPolicyRepository;
import ru.projectx.contracts.repository.PrintedFormRepository;
import ru.projectx.contracts.service.status.IssuanceSnapshot;
import ru.projectx.contracts.service.status.RequestStatusPublication;

@Service
public class IssuedPolicyRecording {

    static final String NOT_REQUESTED = "запрос на выпуск полиса ЛМ ещё не отправлялся";

    private static final Logger log = LoggerFactory.getLogger(IssuedPolicyRecording.class);

    private final LmPolicyRepository policies;
    private final PrintedFormRepository forms;
    private final RequestStatusPublication statuses;

    public IssuedPolicyRecording(LmPolicyRepository policies, PrintedFormRepository forms,
            RequestStatusPublication statuses) {
        this.policies = policies;
        this.forms = forms;
        this.statuses = statuses;
    }

    public PolicyExpectation expect(UUID policyId) {
        LmPolicy policy = load(policyId);
        if (policy.status() == LmPolicyStatus.NEW) {
            throw new ContractViolationException(NOT_REQUESTED);
        }
        return policy.submitted() ? PolicyExpectation.AWAITING_RESULT : PolicyExpectation.ALREADY_FINISHED;
    }

    @Transactional
    public void recordIssued(UUID policyId, IssuedPolicy issued, List<UskDocument> documents, Instant now) {
        applyResult(policyId, now, policy -> {
            policy.markIssued(issued, now);
            forms.saveAll(documents.stream()
                    .map(document -> PrintedForm.issuedForLmPolicy(policy.id(), document, now))
                    .toList());
        });
    }

    @Transactional
    public void recordFailure(UUID policyId, IssueError error, Instant now) {
        applyResult(policyId, now, policy -> policy.markError(error, now));
    }

    private void applyResult(UUID policyId, Instant now, Consumer<LmPolicy> result) {
        LmPolicy policy = load(policyId);
        if (!policy.submitted()) {
            warnUnchanged(policy);
            return;
        }
        IssuanceSnapshot before = statuses.watch(policy.state().request().individualRequestId());
        result.accept(policy);
        statuses.afterRecordChange(before, now);
    }

    @Transactional
    public void recordLate(UUID policyId) {
        warnUnchanged(load(policyId));
    }

    private static void warnUnchanged(LmPolicy policy) {
        log.warn("полис ЛМ {} участника {} заявки {}: результат выпуска получен по полису ЛМ в статусе {}, запись не изменена",
                policy.id(), policy.participantId(), policy.state().request().individualRequestId(), policy.status());
    }

    private LmPolicy load(UUID policyId) {
        return policies.findById(policyId)
                .orElseThrow(() -> new ContractViolationException("полиса ЛМ " + policyId + " в Договорах нет"));
    }
}
