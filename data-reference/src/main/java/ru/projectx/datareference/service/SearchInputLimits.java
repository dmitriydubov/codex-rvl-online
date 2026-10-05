package ru.projectx.datareference.service;

import java.util.List;
import ru.projectx.datareference.exception.InvalidRequestException;

/**
 * Верхние пределы того, что вызывающий может прислать в поиск: длина строки поиска и число кодов в перечне
 * МКБ-10. Считается то, что пришло в запросе, до обрезки пробелов, нормализации и удаления повторов: эти
 * пределы защищают сервис от объёма, а не проверяют смысл запроса. Превышение — ошибка вызывающего с именем
 * поля. Причина называет правило, а не значение: в строке поиска может быть фамилия.
 */
final class SearchInputLimits {

    private static final String QUERY = "query";
    private static final String CODES = "codes";

    private SearchInputLimits() {
    }

    /** Длина строки поиска в символах, а не в байтах: кириллическая буква — один символ. */
    static void requireQueryNotLongerThan(String query, int maxLength) {
        if (query.codePointCount(0, query.length()) > maxLength) {
            throw new InvalidRequestException(QUERY, "не длиннее " + maxLength + " символов");
        }
    }

    static void requireCodesNotMoreThan(List<String> codes, int maxCodes) {
        if (codes.size() > maxCodes) {
            throw new InvalidRequestException(CODES, "не больше " + maxCodes + " кодов");
        }
    }
}
