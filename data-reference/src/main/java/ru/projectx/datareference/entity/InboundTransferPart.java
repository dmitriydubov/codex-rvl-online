package ru.projectx.datareference.entity;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Место сообщения в передаче, которая приходит несколькими сообщениями. Сегодня так приходят только
 * прайс-листы: пакеты строк и одно финальное сообщение со счётчиками.
 *
 * <p>Разметку заполняет сборщик передачи, когда откладывает сообщение в буфер. У сообщений других потоков и
 * у ещё не разобранных сообщений все три значения пустые.
 *
 * @param correlationId идентификатор передачи, общий для всех её сообщений
 * @param batchCount    сколько пакетов строк ожидается в передаче; есть только у финального сообщения
 * @param rowCount      строк в этом пакете, а у финального сообщения — сколько строк ожидается во всей передаче
 */
public record InboundTransferPart(
        @Nullable UUID correlationId, @Nullable Integer batchCount, @Nullable Integer rowCount) {
}
