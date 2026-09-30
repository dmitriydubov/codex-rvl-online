package ru.projectx.datareference.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.Program;

/**
 * Upsert идёт по {@code external_id} — GUID объекта в 1С, а не по нашему первичному
 * ключу: другого устойчивого идентификатора у сообщения источника нет.
 */
@Repository
public interface ProgramRepository extends JpaRepository<Program, UUID> {

    Optional<Program> findByExternalId(UUID externalId);

    /** Программы полиса по GUID из ТЗ-1 — одним запросом на все строки полиса. */
    List<Program> findAllByExternalIdIn(Collection<UUID> externalIds);

    /**
     * Программа вместе с перечнем и учреждениями перечня — одним запросом. Иначе ответ
     * REST собирался бы строкой за строкой: перечень ленивый, и у каждой его строки
     * ленивое учреждение.
     */
    @Query("""
            select p from Program p
              left join fetch p.availableLpu row
              left join fetch row.lpu
             where p.id = :id
            """)
    Optional<Program> findWithServicesById(UUID id);
}
