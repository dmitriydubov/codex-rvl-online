package ru.projectx.datareference.controller.openapi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

record YamlNode(@Nullable Object value) {

    YamlNode get(String key) {
        return new YamlNode(value instanceof Map<?, ?> map ? map.get(key) : null);
    }

    boolean isTrue() {
        return Boolean.TRUE.equals(value);
    }

    Optional<String> text() {
        return value instanceof String text ? Optional.of(text) : Optional.empty();
    }

    Optional<Integer> integer() {
        return value instanceof Integer number ? Optional.of(number) : Optional.empty();
    }

    List<YamlNode> items() {
        return value instanceof List<?> list ? list.stream().map(YamlNode::new).toList() : List.of();
    }

    Set<String> texts() {
        return items().stream()
                .flatMap(item -> item.text().stream())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    Map<String, YamlNode> entries() {
        Map<String, YamlNode> entries = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> entries.put(String.valueOf(key), new YamlNode(child)));
        }
        return entries;
    }
}
