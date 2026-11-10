package ru.projectx.contracts.service.payment.event;

import java.util.regex.Pattern;
import ru.projectx.contracts.service.payment.IgnoredEvent;

public record Ignored(IgnoredEvent reason, String text) {

    static final String MALFORMED_TYPE = "с типом не по формату";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_.-]{1,64}");

    static Ignored notApplicable(String eventType) {
        String shown = IDENTIFIER.matcher(eventType).matches() ? eventType : MALFORMED_TYPE;
        return new Ignored(IgnoredEvent.NOT_APPLICABLE, "событие " + shown + " заказ не меняет");
    }

    static Ignored outOfOrder(String eventType, String standing) {
        return new Ignored(IgnoredEvent.OUT_OF_ORDER, "событие " + eventType + " не по порядку: заказ " + standing);
    }
}
