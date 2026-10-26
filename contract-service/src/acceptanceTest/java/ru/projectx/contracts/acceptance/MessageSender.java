package ru.projectx.contracts.acceptance;

import java.util.Arrays;
import java.util.Set;

enum MessageSender {

    REQUESTS("сервиса Заявок", Set.of()),
    ONEC("1С:УСК", Set.of("LmPolicyIssued", "KmContractRegistered"));

    static final String PATTERN = "сервиса Заявок|1С:УСК";

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
}
