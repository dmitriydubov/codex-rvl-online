package ru.projectx.contracts.service.integration;

import java.io.InputStream;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Код ошибки из единого формата ответа справочника: {@code error.code}. Тело, которое в этот формат не укладывается
 * (страница ошибки прокси, обрезанный ответ), кода не несёт; само тело в журнал не пишется.
 */
final class ReferenceErrorBodies {

    private static final Logger log = LoggerFactory.getLogger(ReferenceErrorBodies.class);

    private static final String ERROR = "error";
    private static final String CODE = "code";

    private final JsonMapper json;

    ReferenceErrorBodies(JsonMapper json) {
        this.json = json;
    }

    Optional<String> code(InputStream body) {
        try {
            JsonNode code = json.readTree(body).path(ERROR).path(CODE);
            return code.isString() ? Optional.of(code.asString()) : Optional.empty();
        }
        catch (JacksonException unreadable) {
            log.debug("тело ошибки справочника не в формате error.code: {}", unreadable.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
