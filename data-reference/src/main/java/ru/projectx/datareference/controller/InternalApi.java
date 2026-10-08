package ru.projectx.datareference.controller;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Отмечает контроллер внутреннего чтения справочников: только {@code GET} под {@code /internal/v1}, наружу не
 * публикуется, доступ ограничивает сетевая политика. Свой {@code @RequestMapping} рядом не склеится с этой аннотацией,
 * а вытеснит её вместе с базовым путём и {@code produces}, поэтому путь ресурса задаётся в {@code @GetMapping}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@RestController
@RequestMapping(path = InternalApi.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public @interface InternalApi {

    /** Общий базовый путь всех ручек справочников. */
    String BASE_PATH = "/internal/v1";
}
