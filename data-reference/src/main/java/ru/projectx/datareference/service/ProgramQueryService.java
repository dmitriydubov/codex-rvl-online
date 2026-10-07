package ru.projectx.datareference.service;

import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceCaches;
import ru.projectx.datareference.dto.ProgramResponse;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.ProgramLpuRepository;
import ru.projectx.datareference.repository.ProgramRepository;

/**
 * Программа страхования по нашему {@code id} с перечнем услуг. Перечень читается вторым запросом — вместе с
 * учреждениями, иначе N+1 на каждую строку, и в устойчивом порядке, который задаёт этот запрос: коллекция программы
 * порядка не хранит, и два чтения одной программы давали бы разный перечень.
 *
 * <p>Транзакция чтения здесь, а не в контроллере: ответ собирается внутри неё, за её границей
 * ленивые связи уже не догрузятся ({@code open-in-view} выключен). Ответ кэшируется по {@code id} программы вместе с
 * порядком перечня.
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
    @RestReadTransaction
    public ProgramResponse byId(UUID id) {
        return programs.findById(id)
                .map(program -> ProgramResponse.of(program, programRows.findOrderedByProgramId(id)))
                .orElseThrow(() -> ReferenceNotFoundException.program(id));
    }
}
