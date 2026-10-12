package ru.projectx.datareference.entity;

import java.util.Objects;
import java.util.UUID;

public record AwaitedParent(ParentKind kind, UUID id) {

    public AwaitedParent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    public String waitingReason() {
        return "ждёт родителя: " + named();
    }

    public String expiredReason() {
        return "срок ожидания родителя истёк: " + named();
    }

    private String named() {
        return kind.title() + " " + id;
    }
}
