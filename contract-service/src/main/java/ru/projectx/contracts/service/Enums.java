package ru.projectx.contracts.service;

import java.util.Arrays;
import java.util.Optional;

public final class Enums {

    private Enums() {
    }

    public static <E extends Enum<E>> Optional<E> named(Class<E> type, String name) {
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equals(name))
                .findFirst();
    }
}
