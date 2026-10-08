package ru.projectx.datareference.controller;

import java.util.UUID;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Подключает строгое чтение UUID ({@link CanonicalUuidEditor}) ко всем контроллерам, в том числе будущим. Это редактор
 * типа, а не конвертер: при отказе конвертера Spring повторяет попытку стандартным редактором, который пропустит
 * неполную запись. Связыватель создаётся на каждый аргумент запроса, поэтому редактор общим не становится.
 */
@ControllerAdvice
public class UuidParameters {

    @InitBinder
    public void readUuidStrictly(WebDataBinder binder) {
        binder.registerCustomEditor(UUID.class, new CanonicalUuidEditor());
    }
}
