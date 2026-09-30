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

    /**
     * Перечень учреждений программы резолвится одним запросом, а не строкой за строкой:
     * в перечне ТЗ-6 приходит под тысячу строк, и поиск по каждой — это тысяча запросов
     * на одно сообщение.
     */
    List<Lpu> findAllByExternalIdIn(Collection<UUID> externalIds);

    /**
     * Каталог R-01: действующие учреждения из перечней действующих программ, у которых
     * строка перечня идёт по виду обращения РВЛ (В-6). Программы — GUID из базовой
     * логики A, виды обращения — GUID из {@code appeal_types}. Сроки строки перечня
     * в отбор не входят (ТЗ на API, вопрос 5). Порядок — по наименованию, как в ответе.
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
