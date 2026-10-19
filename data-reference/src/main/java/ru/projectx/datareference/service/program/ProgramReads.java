package ru.projectx.datareference.service.program;

import java.util.UUID;
import ru.projectx.datareference.dto.ProgramResponse;

public interface ProgramReads {

    ProgramResponse byId(UUID id);
}
