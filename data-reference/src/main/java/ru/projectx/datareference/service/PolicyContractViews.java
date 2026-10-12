package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.PolicyContractView;

public interface PolicyContractViews {

    PolicyContractView byId(UUID id);

    PolicyContractView byExternalId(UUID externalId);
}
