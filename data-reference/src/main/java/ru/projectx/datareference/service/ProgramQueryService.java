package ru.projectx.datareference.service;

import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.config.ReferenceCaches;
import ru.projectx.datareference.dto.ProgramResponse;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.ProgramRepository;

/**
 * Программа страхования по нашему {@code id} — с перечнем услуг одним запросом
 * ({@code join fetch}), иначе N+1 на каждую строку перечня.
 *
 * <p>Транзакция чтения здесь, а не в контроллере: ответ собирается внутри неё, за её границей
 * ленивые связи уже не догрузятся ({@code open-in-view} выключен). Ответ кэшируется по {@code id} программы.
 */
@Service
public class ProgramQueryService implements ProgramReads {

    private final ProgramRepository programs;

    public ProgramQueryService(ProgramRepository programs) {
        this.programs = programs;
    }

    @Override
    @Cacheable(ReferenceCaches.PROGRAMS)
    @RestReadTransaction
    public ProgramResponse byId(UUID id) {
        return programs.findWithServicesById(id)
                .map(ProgramResponse::of)
                .orElseThrow(() -> ReferenceNotFoundException.program(id));
    }
}
