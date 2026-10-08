package ru.projectx.datareference.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import ru.projectx.datareference.entity.Policy;

/**
 * Полисы без персональных данных, только строкой {@link PolicyContractRow}. Отдельный от {@link PolicyRepository}
 * намеренно: у него нет метода, который вернул бы {@link Policy} с ФИО и почтой. {@code ContractView} в именах методов
 * не даёт спутать их с {@code findById} Spring Data.
 */
public interface PolicyContractViewRepository extends Repository<Policy, UUID> {

    Optional<PolicyContractRow> findContractViewById(UUID id);

    Optional<PolicyContractRow> findContractViewByExternalId(UUID externalId);
}
