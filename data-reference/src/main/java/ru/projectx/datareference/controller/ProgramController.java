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

    /** Программа с перечнем услуг в том виде, который ждёт сервис Договоров. */
    @GetMapping("/programs/{id}")
    public ProgramResponse program(@PathVariable UUID id) {
        return programs.byId(id);
    }
}
