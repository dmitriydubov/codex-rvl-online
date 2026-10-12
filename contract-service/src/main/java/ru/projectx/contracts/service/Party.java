package ru.projectx.contracts.service;

import ru.projectx.contracts.entity.PersonRole;
import ru.projectx.contracts.entity.PersonSnapshot;

public record Party(PersonRole role, PersonSnapshot person) {
}
