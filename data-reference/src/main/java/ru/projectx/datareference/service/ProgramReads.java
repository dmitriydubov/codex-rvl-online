package ru.projectx.datareference.service;

import java.util.UUID;
import ru.projectx.datareference.dto.ProgramResponse;

public interface ProgramReads {

    ProgramResponse byId(UUID id);
}
