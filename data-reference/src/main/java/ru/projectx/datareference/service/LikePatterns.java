package ru.projectx.datareference.service;

/**
 * Шаблоны {@code like} из строки поиска (R-04, R-07). Знаки шаблона в самой строке — {@code %}
 * и {@code _} — ищутся как есть: перед ними ставится символ экранирования {@code !}, тот же,
 * что в {@code escape '!'} запросов репозиториев. В ФИО, номерах и кодах МКБ-10 его не бывает,
 * но и он сам экранируется — иначе строка с ним сломала бы шаблон.
 */
final class LikePatterns {

    private static final String ESCAPE = "!";
    private static final String ANY = "%";
    private static final String ONE = "_";

    private LikePatterns() {
    }

    static String escaped(String value) {
        return value.replace(ESCAPE, ESCAPE + ESCAPE).replace(ANY, ESCAPE + ANY).replace(ONE, ESCAPE + ONE);
    }

    /** Значение — начало строки. */
    static String prefix(String value) {
        return escaped(value) + ANY;
    }

    /** Значение — где угодно в строке. */
    static String substring(String value) {
        return ANY + escaped(value) + ANY;
    }
}
