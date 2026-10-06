package ru.projectx.datareference.mapper;

import java.util.Optional;
import ru.projectx.datareference.exception.OneCFieldException;
import tools.jackson.databind.JsonNode;

/**
 * Необязательное целое число из сообщения 1С:УСК: номер слоя, номер строки, уровень иерархии, счётчик передачи.
 *
 * <p>Дробная запись и число за пределами {@code int} — испорченное значение, а не отсутствующее. Обрезанная дробь
 * или подставленное значение легли бы в справочник молча, поэтому разбор отклоняет сообщение с именем поля, но без
 * самого значения. Дробной считается и запись с нулевой дробью, например {@code 2.0}: так же читаются обязательные
 * целые поля ({@link OneCRequiredValues#integer}). За пределами {@code int} оказывается и число из тысяч цифр, каким
 * база возвращает из сырья число, записанное в 1С с порядком.
 */
final class OneCInteger {

    private OneCInteger() {
    }

    /**
     * Целое число поля. Не число JSON — пустой результат.
     *
     * @throws OneCFieldException если число записано дробью или не помещается в {@code int}
     */
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
