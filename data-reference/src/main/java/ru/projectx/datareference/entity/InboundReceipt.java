package ru.projectx.datareference.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Что пришло из 1С, по какому потоку, из какой очереди и когда. Эти сведения записываются один раз при приёме
 * сообщения и больше не меняются.
 *
 * <p>Решения по сообщению принимаются по потоку. Очередь хранится для разбора инцидентов: у прайс-листов именно
 * она отделяет пакет строк от финального сообщения передачи.
 *
 * @param messageId   идентификатор сообщения, который присвоила 1С; он же ключ строки в таблице входящих сообщений
 * @param stream      поток данных, к которому относится сообщение
 * @param sourceQueue очередь брокера, из которой сообщение получено
 * @param receivedAt  момент приёма сообщения сервисом
 */
public record InboundReceipt(UUID messageId, OneCStream stream, String sourceQueue, Instant receivedAt) {
}
