package ru.projectx.datareference.mapper;

import java.util.Optional;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

final class OneCInteger {

    private OneCInteger() {
    }

    static Optional<Integer> read(JsonNode data, String field) {
        return Optional.of(data.path(field))
                .filter(JsonNode::isNumber)
                .map(number -> exact(number, field));
    }

    private static int exact(JsonNode number, String field) {
        if (!number.isIntegralNumber()) {
            throw new OneCFieldException("поле %s не является целым числом".formatted(field));
        }
        if (!number.canConvertToInt()) {
            throw new OneCFieldException("поле %s больше допустимого".formatted(field));
        }
        return number.intValue();
    }
}
