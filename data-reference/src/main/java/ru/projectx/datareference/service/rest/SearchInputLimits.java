package ru.projectx.datareference.service.rest;

import java.util.List;
import ru.projectx.datareference.exception.InvalidRequestException;

public final class SearchInputLimits {

    private static final String QUERY = "query";
    private static final String CODES = "codes";

    private SearchInputLimits() {
    }

    public static void requireQueryNotLongerThan(String query, int maxLength) {
        if (characters(query) > maxLength) {
            throw new InvalidRequestException(QUERY, "не длиннее " + maxLength + " символов");
        }
    }

    public static void requireQueryNotShorterThan(String query, int minLength) {
        if (characters(query) < minLength) {
            throw new InvalidRequestException(QUERY, "не короче " + minLength + " символов");
        }
    }

    public static void requireCodesNotMoreThan(List<String> codes, int maxCodes) {
        if (codes.size() > maxCodes) {
            throw new InvalidRequestException(CODES, "не больше " + maxCodes + " кодов");
        }
    }

    private static int characters(String query) {
        return query.codePointCount(0, query.length());
    }
}
