package ru.projectx.datareference.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.projectx.datareference.dto.ProgramResponse;
import ru.projectx.datareference.service.ReferenceQueryService;

/** Программа страхования (R-09). */
@InternalApi
public class ProgramController {

    private final ReferenceQueryService references;

    public ProgramController(ReferenceQueryService references) {
        this.references = references;
    }

    /** R-09: программа с перечнем услуг, как в ТЗ сервиса Договоров. */
    @GetMapping("/programs/{id}")
    public ProgramResponse program(@PathVariable UUID id) {
        return references.program(id);
    }
}
