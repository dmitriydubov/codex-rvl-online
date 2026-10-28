package ru.projectx.contracts.service.usk;

import ru.projectx.contracts.entity.issuance.IssuanceRecord;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;

public interface UskRequestFacts<R extends IssuanceRecord, F> {

    F gather(R record, ExternalIdentifiers identifiers);
}
