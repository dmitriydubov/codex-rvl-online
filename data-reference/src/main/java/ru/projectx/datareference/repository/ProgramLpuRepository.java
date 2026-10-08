package ru.projectx.datareference.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import ru.projectx.datareference.entity.ProgramLpu;

/**
 * Строки перечня учреждений программы для ответа REST — отдельным запросом, а не коллекцией программы: порядок
 * элементов коллекции Hibernate не сохраняет, а порядок результата запроса задаёт {@code order by}.
 */
public interface ProgramLpuRepository extends Repository<ProgramLpu, UUID> {

    /**
     * Строки перечня программы с учреждениями в полностью заданном порядке, чтобы два чтения одной программы давали
     * один ответ. Учреждения идут по наименованию, как в каталоге. Внутри учреждения строка без даты начала идёт
     * раньше, а строки без вида обращения и без даты окончания — позже.
     */
    @Query("""
            select row from ProgramLpu row
              join fetch row.lpu lpu
             where row.program.id = :programId
             order by lpu.name, lpu.externalId, row.layerNumber, row.beginDate nulls first,
                      row.externalAppealTypeId nulls last, row.endDate nulls last, row.typeRd,
                      row.directAttachment, row.percentCopayment
            """)
    List<ProgramLpu> findOrderedByProgramId(UUID programId);
}
