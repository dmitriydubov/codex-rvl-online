package ru.projectx.datareference.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import ru.projectx.datareference.entity.Policy;

/**
 * Полисы для чтения без персональных данных — только строкой {@link PolicyContractRow}, а не сущностью.
 *
 * <p>Отдельный от {@link PolicyRepository} и узкий намеренно: у него нет метода, который вернул бы
 * {@link Policy} с ФИО, датой рождения и почтой. Тип ответа — проекция DTO: запрос выбирает только свойства,
 * названные компонентами записи, и колонки ПДн из базы не читаются вовсе.
 *
 * <p>{@code ContractView} в именах методов — описательная часть между {@code find} и {@code By}: так
 * запрос не спутать с зарезервированным {@code findById} репозиториев Spring Data.
 */
public interface PolicyContractViewRepository extends Repository<Policy, UUID> {

    Optional<PolicyContractRow> findContractViewById(UUID id);

    Optional<PolicyContractRow> findContractViewByExternalId(UUID externalId);
}
