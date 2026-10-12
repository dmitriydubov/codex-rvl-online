package ru.projectx.contracts.service.reference;

import java.util.UUID;
import ru.projectx.contracts.dto.ReferenceProgram;

public interface ProgramReads {

    ReferenceProgram program(UUID id);
}
