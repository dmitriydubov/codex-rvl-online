package ru.projectx.datareference.service.price;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Подряд идущие ночи от {@code from} включительно до {@code to} не включительно, оплачиваемые по одной строке. */
public record PriceSegment(LocalDate from, LocalDate to, PriceLine line) {

    public int nights() {
        return Math.toIntExact(new StayPeriod(from, to).nights());
    }

    public BigDecimal amount() {
        return forNights(line.price());
    }

    public Optional<BigDecimal> amountWithDiscount() {
        return Optional.ofNullable(line.priceWithDiscount()).map(this::forNights);
    }

    private BigDecimal forNights(BigDecimal pricePerNight) {
        return pricePerNight.multiply(BigDecimal.valueOf(nights()));
    }
}
