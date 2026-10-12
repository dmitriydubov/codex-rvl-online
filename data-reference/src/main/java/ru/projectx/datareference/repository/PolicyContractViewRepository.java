package ru.projectx.datareference.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import ru.projectx.datareference.entity.Policy;

public interface PolicyContractViewRepository extends Repository<Policy, UUID> {

    Optional<PolicyContractRow> findContractViewById(UUID id);

    Optional<PolicyContractRow> findContractViewByExternalId(UUID externalId);
}
