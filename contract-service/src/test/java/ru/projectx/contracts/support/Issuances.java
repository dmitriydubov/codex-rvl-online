package ru.projectx.contracts.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractDraft;
import ru.projectx.contracts.entity.IssuanceTerms;
import ru.projectx.contracts.entity.LmPolicy;
import ru.projectx.contracts.entity.LmPolicyDraft;
import ru.projectx.contracts.entity.RequestReference;
import ru.projectx.contracts.entity.TripPeriod;

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
}
