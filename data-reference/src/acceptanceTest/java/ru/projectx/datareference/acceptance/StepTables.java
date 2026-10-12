package ru.projectx.datareference.acceptance;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

final class StepTables {

    private static final Set<String> FLAGS = Set.of("true", "false");

    private StepTables() {
    }

    static void onlyKnown(Map<String, String> table, String... keys) {
        Set<String> unknown = new TreeSet<>(table.keySet());
        unknown.removeAll(Set.of(keys));
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("в таблице шага строки, которых шаг не знает: " + unknown);
        }
    }

    static boolean flag(Map<String, String> table, String key) {
        String value = required(table, key);
        if (!FLAGS.contains(value)) {
            throw new IllegalArgumentException("строка «" + key + "» — не true и не false: " + value);
        }
        return Boolean.parseBoolean(value);
    }

    static boolean flagOr(Map<String, String> table, String key, boolean absent) {
        return table.containsKey(key) ? flag(table, key) : absent;
    }

    static String required(Map<String, String> table, String key) {
        return Optional.ofNullable(table.get(key))
                .orElseThrow(() -> new IllegalArgumentException("в таблице шага нет строки «" + key + "»"));
    }
}
