package ru.projectx.datareference.controller;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.projectx.datareference.dto.Mkb10Response;
import ru.projectx.datareference.exception.InvalidRequestException;
import ru.projectx.datareference.service.Mkb10Reads;

@InternalApi
public class Mkb10Controller {

    private static final String QUERY = "query";
    private static final String CODES = "codes";

    private final Mkb10Reads mkb10;

    public Mkb10Controller(Mkb10Reads mkb10) {
        this.mkb10 = mkb10;
    }

    @GetMapping(path = "/mkb10", params = {QUERY, "!" + CODES})
    public Mkb10Response searchMkb10(
            @RequestParam String query, @RequestParam(required = false) @Nullable Integer limit) {

        return mkb10.search(query, limit);
    }

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
