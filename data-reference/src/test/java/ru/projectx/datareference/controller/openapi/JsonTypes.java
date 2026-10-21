package ru.projectx.datareference.controller.openapi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

final class JsonTypes {

    static final String OBJECT = "object";
    static final String ARRAY = "array";

    private static final String STRING = "string";
    private static final String INTEGER = "integer";
    private static final String BOOLEAN = "boolean";

    private static final Map<Class<?>, String> SCALARS = Map.ofEntries(
            Map.entry(String.class, STRING),
            Map.entry(UUID.class, STRING),
            Map.entry(LocalDate.class, STRING),
            Map.entry(BigDecimal.class, "number"),
            Map.entry(int.class, INTEGER),
            Map.entry(Integer.class, INTEGER),
            Map.entry(long.class, INTEGER),
            Map.entry(Long.class, INTEGER),
            Map.entry(boolean.class, BOOLEAN),
            Map.entry(Boolean.class, BOOLEAN));

    private JsonTypes() {
    }

    static String of(Class<?> type) {
        if (type.isEnum()) {
            return STRING;
        }
        if (Collection.class.isAssignableFrom(type)) {
            return ARRAY;
        }
        return SCALARS.getOrDefault(type, "не сопоставлен с типом JSON: " + type.getName());
    }
}
