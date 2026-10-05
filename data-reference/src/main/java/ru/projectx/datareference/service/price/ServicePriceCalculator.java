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

/**
 * Считает стоимость услуги за период по строкам её прайса.
 *
 * <p>Каждая строка прайса обрезается по периоду и становится отрезком: ночи, которые оплачиваются по ней. Если два
 * отрезка пересекаются, на одни сутки действуют две строки прайса. Это ошибка данных: выбирать наугад нельзя, и
 * расчёт отказывает с перечнем таких строк. Если между отрезками или по краям периода остались сутки без строки,
 * расчёт тоже отказывает, с перечнем непокрытых интервалов: половина суммы хуже честного отказа. Если в периоде
 * есть и то и другое, отказ — из-за двух строк на одни сутки. Две соседние строки с одинаковой ценой остаются
 * разными отрезками.
 *
 * <p>Расчёт идёт по границам строк, а не по суткам, поэтому его стоимость зависит от числа строк прайса, а не от
 * длины периода.
 */
public final class ServicePriceCalculator {

    /** Порядок конфликтующих строк в отказе: по началу срока, при равном начале — бессрочная последней. */
    private static final Comparator<PriceLine> BY_VALIDITY = Comparator.comparing(PriceLine::validFrom)
            .thenComparing(PriceLine::validTo, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<PriceSegment> BY_START = Comparator.comparing(PriceSegment::from)
            .thenComparing(PriceSegment::to);

    private ServicePriceCalculator() {
    }

    /** Строки прайса могут быть любыми строками услуги: те, что на период не приходятся, расчёт пропускает. */
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

    /** Ночи периода, которые оплачиваются по этой строке; пусто, если строка на период не приходится. */
    private static Optional<PriceSegment> segmentWithin(StayPeriod period, PriceLine line) {
        LocalDate from = line.validFrom().isAfter(period.checkIn()) ? line.validFrom() : period.checkIn();
        LocalDate to = endWithin(period, line);
        return from.isBefore(to) ? Optional.of(new PriceSegment(from, to, line)) : Optional.empty();
    }

    /** День, в который кончается последняя ночь строки в периоде: строка действует по {@code validTo} включительно. */
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

    /** Отрезок пересекается сам с собой, поэтому пересечение с другим — это больше одного совпадения. */
    private static boolean overlapsAnother(List<PriceSegment> segments, PriceSegment segment) {
        return segments.stream().filter(other -> intersect(other, segment)).count() > 1;
    }

    private static boolean intersect(PriceSegment first, PriceSegment second) {
        return first.from().isBefore(second.to()) && second.from().isBefore(first.to());
    }

    /**
     * Отрезки здесь уже не пересекаются и идут по порядку. Непокрытые сутки — между началом периода и первым
     * отрезком, между концом каждого отрезка и началом следующего и между последним отрезком и выездом.
     */
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
