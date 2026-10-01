package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.ProgramResponse;

/** Программа страхования по нашему {@code id} (R-09) — то, что нужно контроллеру программ. */
public interface ProgramReads {

    ProgramResponse byId(UUID id);
}
