package ru.projectx.datareference.service;

import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import ru.projectx.datareference.config.ReferenceCaches;
import ru.projectx.datareference.dto.ProgramResponse;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.ProgramLpuRepository;
import ru.projectx.datareference.repository.ProgramRepository;

/**
 * Программа страхования с перечнем услуг. Перечень читается отдельным запросом вместе с учреждениями и в устойчивом
 * порядке, потому что коллекция программы порядка не хранит. Повторяемое чтение не даёт смешать старую шапку с новым
 * перечнем, если между запросами зафиксируется сообщение 1С: такой ответ пережил бы сброс кэша.
 */
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
                .orElseThrow(() -> ReferenceNotFoundException.program(id));
    }
}
