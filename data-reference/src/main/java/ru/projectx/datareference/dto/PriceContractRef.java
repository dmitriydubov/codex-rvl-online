package ru.projectx.datareference.dto;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.LpuContract;

/**
 * Прайсовый договор ЛПУ в ответах. Вид договора нужен вызывающему: при разборе расхождений в суммах первым
 * делом выясняют, взяты цены с допсоглашения или с главного договора.
 */
public record PriceContractRef(
        UUID id,
        UUID externalId,
        @Nullable String number,
        LocalDate beginDate,
        @Nullable LocalDate endDate,
        boolean uploadedToSob,
        Kind kind) {

    /** {@code MAIN} — главный договор, {@code DOPS} — допсоглашение со своими ценами. */
    public enum Kind {
        MAIN, DOPS
    }

    public static PriceContractRef of(LpuContract contract, Kind kind) {
        return new PriceContractRef(contract.id(), contract.externalId(), contract.number(),
                contract.beginDate(), contract.endDate(), contract.uploadedToSob(), kind);
    }
}
