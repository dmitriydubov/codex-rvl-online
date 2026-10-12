package ru.projectx.datareference.service.price;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import ru.projectx.datareference.exception.AmbiguousPriceException;
import ru.projectx.datareference.exception.PriceNotCoveredException;

public final class ServicePriceCalculator {

    private static final Comparator<PriceLine> BY_VALIDITY = Comparator.comparing(PriceLine::validFrom)
            .thenComparing(PriceLine::validTo, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<PriceSegment> BY_START = Comparator.comparing(PriceSegment::from)
            .thenComparing(PriceSegment::to);

    private ServicePriceCalculator() {
    }

    public static PriceBreakdown calculate(UUID serviceId, StayPeriod period, List<PriceLine> lines) {
        List<PriceSegment> segments = lines.stream()
                .map(line -> segmentWithin(period, line))
                .flatMap(Optional::stream)
                .sorted(BY_START)
                .toList();
        requireNoOverlap(serviceId, segments);
        requireNoGap(serviceId, period, segments);
        return new PriceBreakdown(segments);
    }

    private static Optional<PriceSegment> segmentWithin(StayPeriod period, PriceLine line) {
        LocalDate from = line.validFrom().isAfter(period.checkIn()) ? line.validFrom() : period.checkIn();
        LocalDate to = endWithin(period, line);
        return from.isBefore(to) ? Optional.of(new PriceSegment(from, to, line)) : Optional.empty();
    }

    private static LocalDate endWithin(StayPeriod period, PriceLine line) {
        return Optional.ofNullable(line.validTo())
                .map(lastDay -> lastDay.plusDays(1))
                .filter(end -> end.isBefore(period.checkOut()))
                .orElse(period.checkOut());
    }

    private static void requireNoOverlap(UUID serviceId, List<PriceSegment> segments) {
        List<AmbiguousPriceException.PriceRow> overlapping = segments.stream()
                .filter(segment -> overlapsAnother(segments, segment))
                .map(PriceSegment::line)
                .sorted(BY_VALIDITY)
                .map(line -> new AmbiguousPriceException.PriceRow(line.validFrom(), line.validTo()))
                .toList();
        if (!overlapping.isEmpty()) {
            throw new AmbiguousPriceException(serviceId, overlapping);
        }
    }

    private static boolean overlapsAnother(List<PriceSegment> segments, PriceSegment segment) {
        return segments.stream().filter(other -> intersect(other, segment)).count() > 1;
    }

    private static boolean intersect(PriceSegment first, PriceSegment second) {
        return first.from().isBefore(second.to()) && second.from().isBefore(first.to());
    }

    private static void requireNoGap(UUID serviceId, StayPeriod period, List<PriceSegment> segments) {
        List<LocalDate> gapStarts = Stream.concat(Stream.of(period.checkIn()), segments.stream().map(PriceSegment::to))
                .toList();
        List<LocalDate> gapEnds = Stream.concat(segments.stream().map(PriceSegment::from), Stream.of(period.checkOut()))
                .toList();
        List<PriceNotCoveredException.Interval> uncovered = IntStream.range(0, gapStarts.size())
                .filter(index -> gapStarts.get(index).isBefore(gapEnds.get(index)))
                .mapToObj(index -> new PriceNotCoveredException.Interval(gapStarts.get(index), gapEnds.get(index)))
                .toList();
        if (!uncovered.isEmpty()) {
            throw new PriceNotCoveredException(serviceId, uncovered);
        }
    }
}
