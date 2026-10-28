package ru.projectx.contracts.service.issuance;

import ru.projectx.contracts.entity.party.PersonRole;
import ru.projectx.contracts.entity.party.PersonSnapshot;

public record Party(PersonRole role, PersonSnapshot person) {
}
