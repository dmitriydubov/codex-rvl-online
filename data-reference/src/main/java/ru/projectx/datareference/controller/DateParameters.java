package ru.projectx.datareference.controller;

import java.time.LocalDate;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Подключает строгое чтение дат ({@link IsoDateEditor}) ко всем контроллерам, как {@link UuidParameters} для UUID.
 * Редактор типа Spring применяет раньше службы конвертации, так что чтение по настройкам формата сюда не доходит.
 * Связыватель создаётся на каждый аргумент запроса, поэтому редактор с прочитанным значением общим не становится.
 */
@ControllerAdvice
public class DateParameters {

    @InitBinder
    public void readDatesStrictly(WebDataBinder binder) {
        binder.registerCustomEditor(LocalDate.class, new IsoDateEditor());
    }
}
