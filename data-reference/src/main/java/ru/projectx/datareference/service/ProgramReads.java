package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.ProgramResponse;

/** Чтение программы страхования по нашему {@code id} для контроллера программ. */
public interface ProgramReads {

    ProgramResponse byId(UUID id);
}
