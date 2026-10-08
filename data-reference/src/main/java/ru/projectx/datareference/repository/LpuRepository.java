package ru.projectx.datareference.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.projectx.datareference.entity.Lpu;

/**
 * Upsert идёт по {@code external_id} — GUID объекта в 1С, а не по нашему первичному
 * ключу: другого устойчивого идентификатора у сообщения источника нет.
 */
@Repository
public interface LpuRepository extends JpaRepository<Lpu, UUID> {

    Optional<Lpu> findByExternalId(UUID externalId);

    /** Учреждения перечня программы ищутся одним запросом, а не по строке: в перечне приходит под тысячу строк. */
    List<Lpu> findAllByExternalIdIn(Collection<UUID> externalIds);

    /**
     * Каталог учреждений: действующие учреждения из перечней действующих программ, строки которых идут по видам
     * обращения РВЛ, по наименованию. Сроки строки перечня в отбор не входят (ТЗ на API, вопрос 5).
     */
    @Query("""
            select distinct lpu from ProgramLpu line
              join line.lpu lpu
              join line.program program
             where program.externalId in :programExternalIds
               and program.archive = false
               and lpu.archive = false
               and line.externalAppealTypeId in :appealTypeExternalIds
             order by lpu.name
            """)
    List<Lpu> findRvlCatalog(Collection<UUID> programExternalIds, Collection<UUID> appealTypeExternalIds);
}
