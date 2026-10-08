package ru.projectx.datareference.entity;

import java.util.UUID;

/**
 * Сообщение 1С:УСК, которое база отказалась записать в сырьё; из него складывается карантинная запись. Тело хранится
 * байтами, а не текстом: в нём бывает нулевой символ или нечитаемая последовательность, а в карантине оно должно
 * лежать байт в байт.
 */
public record RejectedMessage(UUID messageId, OneCStream stream, String sourceQueue, byte[] body) {
}
