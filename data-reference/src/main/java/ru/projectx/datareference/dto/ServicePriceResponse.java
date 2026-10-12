package ru.projectx.datareference.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ServicePriceResponse(
        NamedRef service, LocalDate dateFrom, LocalDate dateTo, List<Segment> segments, Total total) {

    public ServicePriceResponse {
        segments = List.copyOf(segments);
    }

    public record Segment(
            LocalDate dateFrom,
            LocalDate dateTo,
            int nights,
            BigDecimal price,
            @Nullable BigDecimal priceWithDiscount,
            BigDecimal amount,
            @Nullable BigDecimal amountWithDiscount) {
    }

    public record Total(int nights, BigDecimal amount, @Nullable BigDecimal amountWithDiscount, Basis basis) {
    }

    public enum Basis {
        DISCOUNT,
        BASE
    }
}
