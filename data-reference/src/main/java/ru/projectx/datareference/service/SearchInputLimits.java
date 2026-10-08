package ru.projectx.datareference.service;

import java.util.List;
import ru.projectx.datareference.exception.InvalidRequestException;

/**
 * Пределы входа поиска в символах Юникода, а не единицах UTF-16. Верхние проверяются по присланному, до нормализации:
 * они защищают от объёма. Нижний проверяется после схлопывания пробелов, но до смены регистра. Причина называет
 * правило, а не значение, потому что в строке поиска может быть фамилия.
 */
final class SearchInputLimits {

    private static final String QUERY = "query";
    private static final String CODES = "codes";

    private SearchInputLimits() {
    }

    static void requireQueryNotLongerThan(String query, int maxLength) {
        if (characters(query) > maxLength) {
            throw new InvalidRequestException(QUERY, "не длиннее " + maxLength + " символов");
        }
    }

    static void requireQueryNotShorterThan(String query, int minLength) {
        if (characters(query) < minLength) {
            throw new InvalidRequestException(QUERY, "не короче " + minLength + " символов");
        }
    }

    static void requireCodesNotMoreThan(List<String> codes, int maxCodes) {
        if (codes.size() > maxCodes) {
            throw new InvalidRequestException(CODES, "не больше " + maxCodes + " кодов");
        }
    }

    private static int characters(String query) {
        return query.codePointCount(0, query.length());
    }
}
