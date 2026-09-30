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
 * Контроллер внутреннего REST-чтения справочников для сервисов Заявок и Договоров. Писать
 * в справочники может только 1С:УСК, поэтому у таких контроллеров нет ничего, кроме {@code GET}.
 *
 * <p>Базовый путь {@link #BASE_PATH} — соглашение контура: внутренние ручки под
 * {@code /internal}, версия в пути, на внешнем периметре не публикуются. Аутентификации
 * нет: способ для контура не выбран ({@code ОВ-10}), доступ — сетевой политикой.
 * Ключи — по решению от 23.09: в пути всегда наш {@code id}, внешний ключ — параметром.
 *
 * <p>Путь и тип ответа заданы здесь один раз на все контроллеры ресурсов: составная
 * аннотация с {@link RequestMapping} Spring MVC читает так же, как если бы та стояла
 * на самом классе. Логики в контроллерах нет: они отдают то, что вернули сервисы,
 * а отказы превращает в единый формат {@link ApiExceptionHandler}.
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
