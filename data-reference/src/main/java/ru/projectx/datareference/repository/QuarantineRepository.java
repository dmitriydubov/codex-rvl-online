package ru.projectx.datareference.repository;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.QuarantinedMessage;

/** Карантин сообщений 1С:УСК, тела которых база не приняла: таблица {@code quarantine}. */
@Repository
public interface QuarantineRepository extends JpaRepository<QuarantinedMessage, UUID> {

    /**
     * Стирает тела не больше {@code limit} карантинных записей, принятых раньше {@code receivedBefore}, начиная
     * с самых старых, и возвращает, сколько стёрто. Записи остаются, тела не читаются в память. Транзакцию
     * открывает вызывающий, отдельную на каждую пачку.
     *
     * <p>SQL PostgreSQL, а не язык запросов JPA: у подзапроса там нет ограничения числа строк. Подходит частичный
     * индекс по записям с телом.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            update quarantine
               set body = null
             where message_id in (select message_id
                                    from quarantine
                                   where body is not null
                                     and received_at < :receivedBefore
                                   order by received_at
                                   limit :limit)
            """)
    int forgetBodies(@Param("receivedBefore") Instant receivedBefore, @Param("limit") int limit);
}
