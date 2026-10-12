package ru.projectx.datareference.controller;

import java.time.LocalDate;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

@ControllerAdvice
public class DateParameters {

    @InitBinder
    public void readDatesStrictly(WebDataBinder binder) {
        binder.registerCustomEditor(LocalDate.class, new IsoDateEditor());
    }
}
