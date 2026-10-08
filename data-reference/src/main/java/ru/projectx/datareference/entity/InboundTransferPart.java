package ru.projectx.datareference.entity;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Место сообщения в передаче из нескольких сообщений; так приходят прайс-листы. Заполняется, когда сборщик
 * откладывает сообщение в буфер. {@code batchCount} есть только у финального сообщения, и его {@code rowCount}
 * считает строки всей передачи, а не одного пакета.
 */
public record InboundTransferPart(
        @Nullable UUID correlationId, @Nullable Integer batchCount, @Nullable Integer rowCount) {
}
