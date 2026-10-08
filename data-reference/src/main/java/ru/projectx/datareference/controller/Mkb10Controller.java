package ru.projectx.datareference.controller;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.projectx.datareference.dto.Mkb10Response;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.service.Mkb10Reads;

/**
 * Справочник МКБ-10 для сервиса Заявок: подсказка по коду или наименованию и раскрытие перечня кодов разом. Режим
 * выбирает ключ {@code query} или {@code codes}. У запроса с обоими ключами и без ключа свои ручки с отказом 400:
 * иначе Spring ответил бы 500 на две подошедшие ручки или назвал бы неверную причину.
 */
@InternalApi
public class Mkb10Controller {

    private static final String QUERY = "query";
    private static final String CODES = "codes";

    private final Mkb10Reads mkb10;

    public Mkb10Controller(Mkb10Reads mkb10) {
        this.mkb10 = mkb10;
    }

    /** Подсказка по МКБ-10: по началу кода или по части наименования. */
    @GetMapping(path = "/mkb10", params = {QUERY, "!" + CODES})
    public Mkb10Response searchMkb10(
            @RequestParam String query, @RequestParam(required = false) @Nullable Integer limit) {

        return mkb10.search(query, limit);
    }

    /** Перечень кодов разом, вместе с архивными: старые заявки ссылаются и на них. */
    @GetMapping(path = "/mkb10", params = {CODES, "!" + QUERY})
    public Mkb10Response mkb10ByCodes(@RequestParam List<String> codes) {
        return mkb10.byCodes(codes);
    }

    @GetMapping(path = "/mkb10", params = {QUERY, CODES})
    public Mkb10Response mkb10WithBothKeys() {
        throw new InvalidRequestException(QUERY, "query и codes вместе не передаются: нужен один из них");
    }

    @GetMapping("/mkb10")
    public Mkb10Response mkb10WithoutKey() {
        throw new InvalidRequestException(QUERY, "нужен query либо codes");
    }
}
