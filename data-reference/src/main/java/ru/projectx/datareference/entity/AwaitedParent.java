package ru.projectx.datareference.entity;

import java.util.Objects;
import java.util.UUID;

/**
 * Запись справочника, которой ещё нет, а сообщение 1С на неё ссылается. Причины ожидания и отказа называют только
 * вид и GUID записи в 1С: это не персональные данные, поэтому причина остаётся в строке сырья и после стирания тела.
 */
public record AwaitedParent(ParentKind kind, UUID id) {

    public AwaitedParent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    /** Причина, с которой сообщение ждёт эту запись. */
    public String waitingReason() {
        return "ждёт родителя: " + named();
    }

    /** Причина окончательного отказа, когда запись не пришла за срок ожидания. */
    public String expiredReason() {
        return "срок ожидания родителя истёк: " + named();
    }

    private String named() {
        return kind.title() + " " + id;
    }
}
