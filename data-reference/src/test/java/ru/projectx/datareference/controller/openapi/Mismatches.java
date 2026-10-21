package ru.projectx.datareference.controller.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

final class Mismatches {

    private final List<String> found = new ArrayList<>();

    void add(String where, String what) {
        found.add(where + ": " + what);
    }

    void whenDiffer(String where, String what, Object inCode, Object described) {
        if (!Objects.equals(inCode, described)) {
            add(where, what + " — в коде " + inCode + ", в описании " + described);
        }
    }

    void compareNames(String where, String kind, Set<String> inCode, Set<String> described) {
        missing(inCode, described).forEach(name -> add(where, kind + " «" + name + "» есть в коде, в описании нет"));
        missing(described, inCode).forEach(name -> add(where, kind + " «" + name + "» есть в описании, в коде нет"));
    }

    List<String> found() {
        return List.copyOf(found);
    }

    void assertNone() {
        assertThat(found)
                .withFailMessage(() -> OpenApiDescription.FILE + " разошёлся с кодом ("
                        + found.size() + "):\n" + String.join("\n", found))
                .isEmpty();
    }

    private static Set<String> missing(Set<String> names, Set<String> in) {
        Set<String> missing = new TreeSet<>(names);
        missing.removeAll(in);
        return missing;
    }
}
