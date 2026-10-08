package ru.projectx.datareference.repository;

import java.time.Instant;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Сколько сырых сообщений одного потока лежит в одном незавершённом статусе и когда принято самое давнее из них.
 * Тел сообщений здесь нет.
 */
public interface StatusBacklog {

    OneCStream getStream();

    InboundStatus getStatus();

    long getMessages();

    Instant getOldestReceivedAt();
}
