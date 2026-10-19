package ru.projectx.datareference.service;

import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import ru.projectx.datareference.config.ReferenceCaches;
import ru.projectx.datareference.dto.ProgramResponse;
import ru.projectx.datareference.exception.MissingReference;
import ru.projectx.datareference.repository.ProgramLpuRepository;
import ru.projectx.datareference.repository.ProgramRepository;
import ru.projectx.datareference.service.rest.RestReadTransaction;

@Service
public class ProgramQueryService implements ProgramReads {

    private final ProgramRepository programs;
    private final ProgramLpuRepository programRows;

    public ProgramQueryService(ProgramRepository programs, ProgramLpuRepository programRows) {
        this.programs = programs;
        this.programRows = programRows;
    }

    @Override
    @Cacheable(ReferenceCaches.PROGRAMS)
    @RestReadTransaction(isolation = Isolation.REPEATABLE_READ)
    public ProgramResponse byId(UUID id) {
        return programs.findById(id)
                .map(program -> ProgramResponse.of(program, programRows.findOrderedByProgramId(id)))
                .orElseThrow(() -> MissingReference.PROGRAM.notFound(id));
    }
}
