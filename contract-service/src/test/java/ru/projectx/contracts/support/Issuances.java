package ru.projectx.contracts.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.contract.ContractDraft;
import ru.projectx.contracts.entity.contract.ContractStatus;
import ru.projectx.contracts.entity.issuance.IssuanceTerms;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;
import ru.projectx.contracts.entity.policy.LmPolicy;
import ru.projectx.contracts.entity.policy.LmPolicyDraft;
import ru.projectx.contracts.entity.issuance.RequestReference;
import ru.projectx.contracts.entity.issuance.TripPeriod;

public final class Issuances {

    public static final Instant OPENED = Instant.parse("2026-02-10T07:05:00Z");
    public static final RequestReference REQUEST =
            new RequestReference(Tasks.REQUEST, Tasks.INSURANCE_CONTRACT, Tasks.INSURER_POLICY);
    public static final TripPeriod PERIOD = new TripPeriod(Tasks.FROM, Tasks.TO);
    public static final IssuanceTerms TERMS = new IssuanceTerms(REQUEST, Tasks.INSURED, Tasks.PROGRAM, PERIOD);

    private Issuances() {
    }

    public static Contract contract(String id, UUID participant, String number) {
        Contract contract = Contract.open(new ContractDraft(number, TERMS, participant, new BigDecimal("34000.00")),
                OPENED);
        ReflectionTestUtils.setField(contract, "id", UUID.fromString(id));
        return contract;
    }

    public static LmPolicy policy(String id, UUID participant) {
        LmPolicy policy = LmPolicy.open(new LmPolicyDraft(TERMS, participant), OPENED);
        ReflectionTestUtils.setField(policy, "id", UUID.fromString(id));
        return policy;
    }

    public static Contract paidContract(String id, UUID participant, String number, Instant paidAt) {
        Contract contract = contract(id, participant, number);
        contract.offerDraft();
        contract.accept();
        contract.markPaid(paidAt, paidAt);
        return contract;
    }

    public static Contract issuedContract(String id, UUID participant, String number) {
        Contract contract = contract(id, participant, number);
        ReflectionTestUtils.setField(contract, "status", ContractStatus.ISSUED);
        return contract;
    }

    public static LmPolicy issuedPolicy(String id, UUID participant) {
        LmPolicy policy = policy(id, participant);
        policy.submit(OPENED);
        policy.markIssued(new IssuedPolicy(UUID.fromString("cb904f2a-7d61-4e08-89a5-40b1e3c7f012"), "LM-000012204",
                UUID.fromString("13ab5388-61ee-4e21-8a46-c325478b78c0"), "0555123456"), OPENED);
        return policy;
    }
}
