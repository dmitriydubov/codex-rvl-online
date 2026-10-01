package ru.projectx.datareference.controller;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.projectx.datareference.dto.Mkb10Response;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.service.Mkb10Reads;

/**
 * Справочник МКБ-10 (R-07): подсказка по коду или наименованию и раскрытие перечня кодов.
 * Запрос без ключа — «нет параметра», а не «не найдено».
 */
@InternalApi
public class Mkb10Controller {

    private final Mkb10Reads mkb10;

    public Mkb10Controller(Mkb10Reads mkb10) {
        this.mkb10 = mkb10;
    }

    /** R-07: подсказка по МКБ-10 — по коду или по наименованию. */
    @GetMapping(path = "/mkb10", params = "query")
    public Mkb10Response searchMkb10(
            @RequestParam String query, @RequestParam(required = false) @Nullable Integer limit) {

        return mkb10.search(query, limit);
    }

    /** R-07: раскрыть перечень кодов разом, вместе с архивными. */
    @GetMapping(path = "/mkb10", params = "codes")
    public Mkb10Response mkb10ByCodes(@RequestParam List<String> codes) {
        return mkb10.byCodes(codes);
    }

    @GetMapping("/mkb10")
    public Mkb10Response mkb10WithoutKey() {
        throw new InvalidRequestException("query", "нужен query либо codes");
    }
}
