package ru.projectx.datareference.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.InboxEntry;

/**
 * Журнал входящих. Своих методов не заводит: ключ таблицы — сам {@code message-id},
 * поэтому {@code existsById} и {@code save} из {@link JpaRepository} — это ровно
 * «видели ли мы такое сообщение» и «запомнить, что видели».
 */
@Repository
public interface InboxRepository extends JpaRepository<InboxEntry, UUID> {
}
