package ru.projectx.datareference.service.price;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Отрезок расчёта: подряд идущие ночи с {@code from} включительно до {@code to} не включительно, которые все
 * оплачиваются по одной строке прайса.
 */
public record PriceSegment(LocalDate from, LocalDate to, PriceLine line) {

    public int nights() {
        return Math.toIntExact(ChronoUnit.DAYS.between(from, to));
    }

    /** Сумма по цене без скидки. Копейки сохраняются: цена умножается на целое число ночей. */
    public BigDecimal amount() {
        return forNights(line.price());
    }

    /** Сумма по цене со скидкой; её нет, если у строки прайса нет цены со скидкой. */
    public Optional<BigDecimal> amountWithDiscount() {
        return Optional.ofNullable(line.priceWithDiscount()).map(this::forNights);
    }

    private BigDecimal forNights(BigDecimal pricePerNight) {
        return pricePerNight.multiply(BigDecimal.valueOf(nights()));
    }
}
