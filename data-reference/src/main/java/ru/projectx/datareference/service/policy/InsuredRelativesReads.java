package ru.projectx.datareference.service.policy;

import java.util.UUID;
import ru.projectx.datareference.dto.InsuredRelativesResponse;

public interface InsuredRelativesReads {

    InsuredRelativesResponse ofPolicy(UUID externalId);

    InsuredRelativesResponse ofPolicyNumber(String number, UUID contractExternalId);
}
