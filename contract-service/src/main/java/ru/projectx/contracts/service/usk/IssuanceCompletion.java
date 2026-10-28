package ru.projectx.contracts.service.usk;

import java.time.Instant;
import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.entity.issuance.IssuedPolicy;

@FunctionalInterface
public interface IssuanceCompletion<R extends IssuanceRecord> {

    void issue(R record, IssuedPolicy issued, Instant now);
}
