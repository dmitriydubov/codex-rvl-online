package ru.projectx.datareference.repository.catalog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import ru.projectx.datareference.entity.catalog.ProgramLpu;

public interface ProgramLpuRepository extends Repository<ProgramLpu, UUID> {

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
