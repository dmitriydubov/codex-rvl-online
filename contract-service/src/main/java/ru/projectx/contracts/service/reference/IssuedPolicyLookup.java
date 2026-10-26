package ru.projectx.contracts.service.reference;

import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.exception.ContractViolationException;
import ru.projectx.contracts.exception.ReferenceArchivedException;
import ru.projectx.contracts.exception.ReferenceKind;
import ru.projectx.contracts.exception.ReferenceRejectedException;
import ru.projectx.contracts.exception.ReferenceUnavailableException;
import ru.projectx.contracts.exception.TemporaryFailureException;

@Component
public class IssuedPolicyLookup {

    static final String REFERENCE_UNAVAILABLE = "справочный сервис недоступен";

    private final PolicyReads policies;
    private final ReferenceMetrics metrics;

    public IssuedPolicyLookup(PolicyReads policies, ReferenceMetrics metrics) {
        this.policies = policies;
        this.metrics = metrics;
    }

    public UUID find(UUID policyExternalId) {
        try {
            return finalAnswer(policyExternalId);
        }
        catch (ReferenceUnavailableException unavailable) {
            throw postponed(policyExternalId, unavailable);
        }
    }

    private UUID finalAnswer(UUID policyExternalId) {
        try {
            return policies.policyByExternalId(policyExternalId).id();
        }
        catch (ReferenceArchivedException archived) {
            throw new ContractViolationException("выпущенный полис " + policyExternalId
                    + " в архиве справочного сервиса", archived);
        }
        catch (ReferenceRejectedException rejected) {
            throw new ContractViolationException("справочный сервис отклонил поиск полиса " + policyExternalId
                    + ": " + rejected.code(), rejected);
        }
    }

    private TemporaryFailureException postponed(UUID policyExternalId, ReferenceUnavailableException unavailable) {
        if (unavailable.reason() == ReferenceUnavailableException.Reason.POLICY_NOT_RECEIVED) {
            return new TemporaryFailureException("полис " + policyExternalId + " ещё не получен справочным сервисом",
                    unavailable);
        }
        metrics.countUnavailable(ReferenceKind.POLICY);
        return new TemporaryFailureException(REFERENCE_UNAVAILABLE, unavailable);
    }
}
