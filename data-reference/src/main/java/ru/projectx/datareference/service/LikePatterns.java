package ru.projectx.datareference.service;

/**
 * Строит шаблоны {@code like} для поиска полисов и МКБ-10, в которых {@code %} и {@code _} из строки ищутся как есть.
 * Экранирует их символ {@code !}, тот же, что в {@code escape '!'} запросов репозиториев; сам он тоже экранируется.
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

    static String prefix(String value) {
        return escaped(value) + ANY;
    }

    static String substring(String value) {
        return ANY + escaped(value) + ANY;
    }
}
