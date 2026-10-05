package ru.projectx.datareference.controller;

import java.time.LocalDate;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Подключает строгое чтение дат ({@link IsoDateEditor}) ко всем контроллерам сразу, так же и по тем же причинам,
 * что и строгое чтение UUID в {@link UuidParameters}: правило одно для каждой ручки, которая принимает дату.
 *
 * <p>Назначенный типу редактор Spring Framework 7.0.8 применяет раньше службы конвертации и при нём службу не
 * вызывает, поэтому стандартное чтение даты, которое зависит от настроек формата, сюда не доходит. Новый
 * связыватель создаётся на каждый аргумент запроса, и редактор с прочитанным значением общим не становится.
 */
@ControllerAdvice
public class DateParameters {

    @InitBinder
    public void readDatesStrictly(WebDataBinder binder) {
        binder.registerCustomEditor(LocalDate.class, new IsoDateEditor());
    }
}
