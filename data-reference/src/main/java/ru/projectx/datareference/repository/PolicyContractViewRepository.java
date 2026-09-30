package ru.projectx.datareference.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import ru.projectx.datareference.entity.Policy;

/**
 * Полисы для чтения без персональных данных — только проекцией, а не сущностью.
 *
 * <p>Отдельный от {@link PolicyRepository} и узкий намеренно. Класс проекции задаёт вызывающий
 * (динамическая проекция Spring Data), и запрос выбирает только свойства по именам компонентов её
 * конструктора — колонки ПДн из базы не читаются вовсе. Проекция — запись ({@code T extends Record}):
 * сущность {@link Policy} с ФИО, датой рождения и почтой через этот репозиторий не запросить,
 * такой вызов не компилируется. Сама проекция передаётся параметром, а не названа здесь: репозиторий
 * не зависит от тел ответов REST.
 *
 * <p>{@code ContractView} в именах методов — описательная часть между {@code find} и {@code By}: так
 * запрос не спутать с зарезервированным {@code findById} репозиториев Spring Data.
 */
public interface PolicyContractViewRepository extends Repository<Policy, UUID> {

    <T extends Record> Optional<T> findContractViewById(UUID id, Class<T> projection);

    <T extends Record> Optional<T> findContractViewByExternalId(UUID externalId, Class<T> projection);
}
