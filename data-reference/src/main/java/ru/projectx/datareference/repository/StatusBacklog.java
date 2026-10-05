package ru.projectx.datareference.repository;

import java.time.Instant;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.entity.OneCStream;

/**
 * Сколько сырых сообщений одного потока лежит в одном незавершённом статусе и когда принято самое давнее из них.
 * Тел сообщений здесь нет.
 */
public interface StatusBacklog {

    /** Поток 1С. */
    OneCStream getStream();

    /** Статус сообщений. */
    InboundStatus getStatus();

    /** Сколько сообщений потока в этом статусе. */
    long getMessages();

    /** Когда принято самое давнее сообщение потока в этом статусе. */
    Instant getOldestReceivedAt();
}
