package ru.projectx.datareference.service.price;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.PriceListItem;

/**
 * Строка прайса для расчёта стоимости: действует по {@code validTo} включительно, пустой {@code validTo} значит
 * бессрочно. Идентификатор нужен, чтобы две строки с одинаковыми датами и ценами не слились в одну.
 */
public record PriceLine(
        UUID id,
        LocalDate validFrom,
        @Nullable LocalDate validTo,
        BigDecimal price,
        @Nullable BigDecimal priceWithDiscount) {

    public static PriceLine of(PriceListItem item) {
        return new PriceLine(item.id(), item.validFrom(), item.validTo(), item.price(), item.priceWithDiscount());
    }
}
