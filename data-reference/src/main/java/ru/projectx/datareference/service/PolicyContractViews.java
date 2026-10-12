package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.PolicyContractView;
import ru.projectx.datareference.exception.ReferenceNotFoundException;

public interface PolicyContractViews {

    PolicyContractView byId(UUID id);

    PolicyContractView byExternalId(UUID externalId);
}
