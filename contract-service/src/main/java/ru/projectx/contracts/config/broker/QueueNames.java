package ru.projectx.contracts.config.broker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class QueueNames {

    private QueueNames() {
    }

    static void requireDistinct(String... names) {
        Set<String> seen = new HashSet<>();
        for (String name : Arrays.stream(names).filter(Objects::nonNull).toList()) {
            if (!seen.add(name)) {
                throw new IllegalArgumentException("имя очереди задано двум свойствам: " + name);
            }
        }
    }
}
