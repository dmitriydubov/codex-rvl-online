package ru.projectx.datareference.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Многочастная передача прайса, которая ждёт своих частей в буфере: её идентификатор у источника, когда
 * принята первая часть и сколько частей уже есть. Тел сообщений здесь нет.
 */
public interface BufferedTransfer {

    /** Идентификатор передачи у источника, общий для всех её сообщений ({@code cid}). */
    UUID getCorrelationId();

    /** Когда принята самая ранняя из частей, что сейчас лежат в буфере: с этого момента передача ждёт. */
    Instant getFirstPartReceivedAt();

    /** Сколько сообщений передачи лежит в буфере. */
    long getParts();
}
