package ru.projectx.datareference.controller;

import java.util.UUID;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Подключает строгое чтение UUID ко всем контроллерам сразу, поэтому сами контроллеры правило
 * не повторяют.
 *
 * <p>Правило подключено как редактор типа UUID, а не как конвертер. Spring MVC сначала ищет
 * редактор, назначенный для типа. Если редактора нет и конвертер отказал, Spring повторяет попытку
 * стандартным редактором UUID, а тот читает неполную запись. Назначенный редактор такой повторной
 * попытки не допускает. Общая служба конвертации приложения, через которую читаются настройки,
 * при этом не меняется.
 */
@ControllerAdvice
public class UuidParameters {

    @InitBinder
    public void readUuidStrictly(WebDataBinder binder) {
        binder.registerCustomEditor(UUID.class, new CanonicalUuidEditor());
    }
}
