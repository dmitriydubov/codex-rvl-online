package ru.projectx.datareference.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Что пришло из 1С, по какому потоку, из какой очереди и когда; записывается один раз при приёме. Решения
 * принимаются по потоку, а очередь хранится для разбора инцидентов и у прайс-листов отделяет пакет строк
 * от финального сообщения передачи.
 */
public record InboundReceipt(UUID messageId, OneCStream stream, String sourceQueue, Instant receivedAt) {
}
