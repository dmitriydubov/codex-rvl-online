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
     * Строки перечня программы вместе с учреждениями, в устойчивом порядке. Учреждения идут по наименованию — так же,
     * как каталог учреждений, сравнение строк делает база; одноимённые — по GUID учреждения в 1С. Строки одного
     * учреждения — по слою, дате начала (без начала — первой: действует с самого начала), виду обращения (без вида —
     * последней), дате окончания (открытая — последней), затем депозитная часть ({@code D}) раньше рисковой
     * ({@code R}), без прямого прикрепления раньше, меньший процент сооплаты раньше — чтобы два чтения одной программы
     * всегда давали один и тот же ответ.
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
