package ru.projectx.contracts.acceptance;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

final class StepTables {

    private StepTables() {
    }

    static void onlyKnown(Map<String, String> table, Set<String> keys) {
        Set<String> unknown = new TreeSet<>(table.keySet());
        unknown.removeAll(keys);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("в таблице шага строки, которых шаг не знает: " + unknown);
        }
    }

    static String required(Map<String, String> table, String key) {
        return Optional.ofNullable(table.get(key))
                .orElseThrow(() -> new IllegalArgumentException("в таблице шага нет строки «" + key + "»"));
    }

    static Optional<String> cell(Map<String, String> table, String key) {
        return Optional.ofNullable(table.get(key)).filter(value -> !value.isBlank());
    }
}
