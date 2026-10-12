package ru.projectx.contracts.service.reference;

import java.util.UUID;
import ru.projectx.contracts.dto.ReferencePolicy;

public interface PolicyReads {

    ReferencePolicy policy(UUID id);

    ReferencePolicy policyByExternalId(UUID externalId);
}
