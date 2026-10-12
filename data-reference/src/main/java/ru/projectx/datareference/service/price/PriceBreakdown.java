package ru.projectx.datareference.service.price;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record PriceBreakdown(List<PriceSegment> segments) {

    public PriceBreakdown {
        segments = List.copyOf(segments);
    }

    public int nights() {
        return segments.stream().mapToInt(PriceSegment::nights).sum();
    }

    public BigDecimal amount() {
        return segments.stream().map(PriceSegment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Optional<BigDecimal> amountWithDiscount() {
        if (!discountedThroughout()) {
            return Optional.empty();
        }
        return Optional.of(segments.stream()
                .map(segment -> segment.amountWithDiscount().orElseThrow())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public boolean discountedThroughout() {
        return segments.stream().allMatch(segment -> segment.amountWithDiscount().isPresent());
    }
}
