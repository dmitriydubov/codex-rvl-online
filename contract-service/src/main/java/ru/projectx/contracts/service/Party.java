package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.party.PersonRole;
import ru.projectx.contracts.entity.party.PersonSnapshot;

public record Party(PersonRole role, PersonSnapshot person) {
}
