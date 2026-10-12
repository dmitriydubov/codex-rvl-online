package ru.projectx.contracts.config;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Проверка имён очередей одного брокера при чтении конфигурации. Одно имя в двух свойствах означает, что два
 * потока обмена повиснут на одной очереди, поэтому такая конфигурация не даёт сервису запуститься.
 */
final class QueueNames {

    private QueueNames() {
    }

    /** Пустые имена пропускаются: их отдельно отклоняет проверка обязательных свойств. */
    static void requireDistinct(String... names) {
        Set<String> seen = new HashSet<>();
        for (String name : Arrays.stream(names).filter(Objects::nonNull).toList()) {
            if (!seen.add(name)) {
                throw new IllegalArgumentException("имя очереди задано двум свойствам: " + name);
            }
        }
    }
}
