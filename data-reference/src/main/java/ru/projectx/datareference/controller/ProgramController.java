package ru.projectx.datareference.controller;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.projectx.datareference.dto.ProgramResponse;
import ru.projectx.datareference.service.ProgramReads;

/** Программа страхования (R-09). */
@InternalApi
public class ProgramController {

    private final ProgramReads programs;

    public ProgramController(ProgramReads programs) {
        this.programs = programs;
    }

    /** R-09: программа с перечнем услуг, как в ТЗ сервиса Договоров. */
    @GetMapping("/programs/{id}")
    public ProgramResponse program(@PathVariable UUID id) {
        return programs.byId(id);
    }
}
