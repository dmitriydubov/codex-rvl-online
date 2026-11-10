package ru.projectx.contracts.acceptance;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

enum MessageSender {

    REQUESTS("сервиса Заявок", Set.of()),
    ONEC("1С:УСК", Set.of("LmPolicyIssued", "KmContractRegistered")),
    ORDERING("Ordering", Set.of("PaymentOrderEvent"));

    static final String PATTERN = "сервиса Заявок|1С:УСК|Ordering";

    private static final Pattern GUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final String alias;
    private final Set<String> eventTypes;

    MessageSender(String alias, Set<String> eventTypes) {
        this.alias = alias;
        this.eventTypes = eventTypes;
    }

    static MessageSender called(String alias) {
        return Arrays.stream(values()).filter(sender -> sender.alias.equals(alias)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("стенд не знает отправителя «" + alias + "»"));
    }

    String alias() {
        return alias;
    }

    boolean sends(String eventType) {
        if (!eventTypes.isEmpty()) {
            return eventTypes.contains(eventType);
        }
        return Arrays.stream(values()).noneMatch(other -> other.eventTypes.contains(eventType));
    }

    String messageId(String name) {
        if (this != ORDERING || GUID.matcher(name).matches()) {
            return name;
        }
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
