package ru.projectx.datareference.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import ru.projectx.datareference.entity.Policy;

/**
 * Полисы для чтения без персональных данных — только проекцией, никогда сущностью целиком.
 *
 * <p>Отдельный от {@link PolicyRepository} и узкий намеренно: у него нет ни одного метода, который
 * вернул бы {@link Policy} с ФИО, датой рождения и почтой, а класс проекции задаёт вызывающий
 * (динамическая проекция Spring Data), и запрос выбирает только поля её конструктора — колонки ПДн
 * из базы не читаются вовсе. Проекция передаётся параметром, а не названа здесь: репозиторий
 * не зависит от тел ответов REST.
 */
public interface PolicyContractViewRepository extends Repository<Policy, UUID> {

    <T> Optional<T> findById(UUID id, Class<T> projection);

    <T> Optional<T> findByExternalId(UUID externalId, Class<T> projection);
}
