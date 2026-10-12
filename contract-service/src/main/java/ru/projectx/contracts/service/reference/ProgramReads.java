package ru.projectx.contracts.service.reference;

import java.util.UUID;
import ru.projectx.contracts.dto.ReferenceProgram;
import ru.projectx.contracts.exception.ReferenceArchivedException;
import ru.projectx.contracts.exception.ReferenceNotFoundException;
import ru.projectx.contracts.exception.ReferenceUnavailableException;

/** Чтение программы страхования из сервиса справочных данных по её идентификатору в справочнике. */
public interface ProgramReads {

    /**
     * Живая программа по идентификатору справочника.
     *
     * @throws ReferenceNotFoundException программы с таким идентификатором нет
     * @throws ReferenceArchivedException программа помечена в 1С:УСК на удаление
     * @throws ReferenceUnavailableException справочник не ответил и после повторов
     */
    ReferenceProgram program(UUID id);
}
