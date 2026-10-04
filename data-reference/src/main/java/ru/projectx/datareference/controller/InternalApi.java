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
 * Отмечает контроллер внутреннего REST-чтения справочников для сервисов Заявок и Договоров. Писать в
 * справочники может только 1С:УСК, поэтому у таких контроллеров есть только {@code GET}.
 *
 * <p>Базовый путь {@link #BASE_PATH} следует соглашению соседних сервисов: внутренние ручки лежат под
 * {@code /internal}, версия входит в путь, и на внешнем периметре такие ручки не публикуются.
 * Аутентификации между сервисами нет, потому что способ для них ещё не выбран; доступ ограничивает
 * сетевая политика. В пути всегда стоит наш {@code id}, а идентификатор из 1С передаётся параметром.
 *
 * <p>Путь и тип ответа заданы здесь один раз на все контроллеры ресурсов. Spring MVC читает составную
 * аннотацию с {@link RequestMapping} так же, как если бы та стояла на самом классе. Поэтому путь ресурса
 * задаётся только в {@code @GetMapping} методов. Собственный {@code @RequestMapping} рядом с этой
 * аннотацией не склеится с ней, а вытеснит её: Spring возьмёт одну из двух с предупреждением в журнале,
 * и пропадут базовый путь и {@code produces}.
 *
 * <p>Логики в контроллерах нет: они отдают то, что вернули сервисы. Отказы в единый формат ошибки
 * переводят {@link ReferenceErrorAdvice}, {@link RequestErrorAdvice} и {@link ServerFailureAdvice}.
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
