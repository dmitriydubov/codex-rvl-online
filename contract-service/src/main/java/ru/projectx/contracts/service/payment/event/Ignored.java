package ru.projectx.contracts.service.payment.event;

import ru.projectx.contracts.service.payment.IgnoredEvent;

public record Ignored(IgnoredEvent reason, String text) {

    static Ignored notApplicable(String eventType) {
        return new Ignored(IgnoredEvent.NOT_APPLICABLE, "событие " + eventType + " заказ не меняет");
    }

    static Ignored outOfOrder(String eventType, String standing) {
        return new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие " + eventType + " не по порядку: заказ " + standing);
    }
}
