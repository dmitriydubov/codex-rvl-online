package ru.projectx.datareference.service;

import java.util.List;
import ru.projectx.datareference.exception.InvalidRequestException;

/**
 * Пределы того, что вызывающий может прислать в поиск: длина строки поиска и число кодов в перечне МКБ-10.
 * Верхние пределы считаются по тому, что пришло в запросе, до обрезки пробелов, нормализации и удаления
 * повторов: они защищают сервис от объёма, а не проверяют смысл запроса. Нижний предел длины считается после
 * обрезки пробелов по краям и схлопывания пробелов внутри, но до смены регистра, по символам, которые прислал
 * вызывающий: в строке из одних пробелов искать нечего. Нарушение — ошибка вызывающего
 * с именем поля. Причина называет правило, а не значение: в строке поиска может быть фамилия.
 *
 * <p>Длина везде считается в символах Юникода, а не в единицах UTF-16 строки Java. Кириллическая буква и эмодзи
 * — по одному символу, хотя эмодзи занимает в строке Java две единицы.
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
