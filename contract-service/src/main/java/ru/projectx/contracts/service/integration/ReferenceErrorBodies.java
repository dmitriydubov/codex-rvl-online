package ru.projectx.contracts.service.integration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Код ошибки из единого формата ответа справочника: {@code error.code}. Тело, которое в этот формат не укладывается
 * (страница ошибки прокси, обрезанный ответ) или длиннее нескольких килобайт, кода не несёт; само тело в журнал не пишется.
 */
final class ReferenceErrorBodies {

    private static final Logger log = LoggerFactory.getLogger(ReferenceErrorBodies.class);

    /** Ошибка справочника — сотни байт; больше присылает только чужая страница ошибки, читать её целиком незачем. */
    static final int MAX_BODY_BYTES = 16 * 1024;

    private static final String ERROR = "error";
    private static final String CODE = "code";

    private final JsonMapper json;

    ReferenceErrorBodies(JsonMapper json) {
        this.json = json;
    }

    Optional<String> code(InputStream body) {
        try {
            byte[] head = body.readNBytes(MAX_BODY_BYTES + 1);
            if (head.length > MAX_BODY_BYTES) {
                log.debug("тело ошибки справочника длиннее {} байт, не разбирается", MAX_BODY_BYTES);
                return Optional.empty();
            }
            return code(json.readTree(head));
        }
        catch (JacksonException | IOException unreadable) {
            log.debug("тело ошибки справочника не в формате error.code: {}", unreadable.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static Optional<String> code(JsonNode body) {
        JsonNode code = body.path(ERROR).path(CODE);
        return code.isString() ? Optional.of(code.asString()) : Optional.empty();
    }
}
