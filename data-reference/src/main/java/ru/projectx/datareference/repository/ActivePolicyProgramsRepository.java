package ru.projectx.datareference.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ActivePolicyProgramsRepository {

    List<UUID> findRvlProgramExternalIdsOfActivePolicies(UUID contractExternalId, LocalDate today);
}
