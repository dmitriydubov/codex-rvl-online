package ru.projectx.datareference.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.Program;

/**
 * Upsert идёт по {@code external_id} — GUID объекта в 1С, а не по нашему первичному
 * ключу: другого устойчивого идентификатора у сообщения источника нет.
 */
@Repository
public interface ProgramRepository extends JpaRepository<Program, UUID> {

    Optional<Program> findByExternalId(UUID externalId);

    /** Программы полиса по GUID из строк программ в сообщении 1С о полисе — одним запросом на все строки. */
    List<Program> findAllByExternalIdIn(Collection<UUID> externalIds);
}
