package ru.projectx.datareference.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * Многочастная передача прайса, которая ждёт своих частей в буфере: её {@code cid}, момент приёма самой ранней из
 * лежащих в буфере частей и число частей. Тел сообщений здесь нет.
 */
public interface BufferedTransfer {

    UUID getCorrelationId();

    Instant getFirstPartReceivedAt();

    long getParts();
}
