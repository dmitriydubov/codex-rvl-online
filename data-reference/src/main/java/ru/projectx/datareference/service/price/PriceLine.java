package ru.projectx.datareference.service.price;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.PriceListItem;

/**
 * Строка прайса услуги в том виде, в каком она нужна расчёту стоимости: срок действия и две цены за сутки.
 *
 * <p>Строка действует с {@code validFrom} по {@code validTo} включительно; пустой {@code validTo} значит, что
 * срок не ограничен. Цены со скидкой у строки может не быть. Идентификатор нужен, чтобы две разные строки с
 * одинаковыми датами и ценами не слились в одну: наружу он не отдаётся.
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

    /** Действует ли строка на ночь, которая начинается в этот день. */
    boolean covers(LocalDate night) {
        return !night.isBefore(validFrom) && (validTo == null || !night.isAfter(validTo));
    }
}
