package ru.projectx.contracts.service.status;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

final class ParticipantRecords {

    private ParticipantRecords() {
    }

    static <T> Map<UUID, T> latestBy(List<T> items, Function<T, UUID> participant, Function<T, UUID> order) {
        return items.stream().collect(Collectors.toUnmodifiableMap(participant, Function.identity(),
                BinaryOperator.maxBy(Comparator.comparing(order))));
    }

    static <V> V latest(Map<UUID, V> byParticipant, UUID participantId, String record) {
        return Optional.ofNullable(byParticipant.get(participantId))
                .orElseThrow(() -> new IllegalStateException("у участника " + participantId + " нет " + record));
    }
}
