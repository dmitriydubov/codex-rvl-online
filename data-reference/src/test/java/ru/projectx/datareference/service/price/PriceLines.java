package ru.projectx.datareference.service.price;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class PriceLines {

    private PriceLines() {
    }

    static PriceLine line(LocalDate validFrom, @Nullable LocalDate validTo, String price, @Nullable String discounted) {
        return new PriceLine(UUID.randomUUID(), validFrom, validTo, new BigDecimal(price),
                Optional.ofNullable(discounted).map(BigDecimal::new).orElse(null));
    }
}
