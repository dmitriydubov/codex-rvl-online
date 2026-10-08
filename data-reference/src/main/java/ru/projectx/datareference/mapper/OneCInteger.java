package ru.projectx.datareference.mapper;

import java.util.Optional;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

/**
 * Целое число из сообщения 1С:УСК, одно правило для обязательных и необязательных полей. Дробь, даже {@code 2.0},
 * и число за пределами {@code int} отклоняют сообщение, а не обрезаются: иначе испорченное значение легло бы молча.
 */
final class OneCInteger {

    private OneCInteger() {
    }

    /** @throws OneCFieldException если число записано дробью или не помещается в {@code int} */
    static Optional<Integer> read(JsonNode data, String field) {
        return Optional.of(data.path(field))
                .filter(JsonNode::isNumber)
                .map(number -> exact(number, field));
    }

    /** Дробь проверяется раньше предела: дробь за пределом {@code int} — тоже дробь, а не превышение. */
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
