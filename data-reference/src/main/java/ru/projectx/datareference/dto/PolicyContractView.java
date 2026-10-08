package ru.projectx.datareference.dto;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Полис для сервиса Договоров: номер, период действия и признак архива. Персональных данных застрахованного
 * здесь нет намеренно: сервис Договоров получает их от сервиса Заявок, а лишнее поле стало бы утечкой.
 */
public record PolicyContractView(
        UUID id,
        UUID externalId,
        String number,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        boolean isArchive) {
}
