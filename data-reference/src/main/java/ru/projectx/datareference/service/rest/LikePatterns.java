package ru.projectx.datareference.service.rest;

public final class LikePatterns {

    private static final String ESCAPE = "!";
    private static final String ANY = "%";
    private static final String ONE = "_";

    private LikePatterns() {
    }

    static String escaped(String value) {
        return value.replace(ESCAPE, ESCAPE + ESCAPE).replace(ANY, ESCAPE + ANY).replace(ONE, ESCAPE + ONE);
    }

    public static String prefix(String value) {
        return escaped(value) + ANY;
    }

    public static String substring(String value) {
        return ANY + escaped(value) + ANY;
    }
}
