package ru.projectx.datareference.service.price;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ru.projectx.datareference.exception.AmbiguousPriceException;
import ru.projectx.datareference.exception.PriceNotCoveredException;

/**
 * Считает стоимость услуги за период по строкам её прайса.
 *
 * <p>Для каждых суток периода ищется строка прайса, которая на них действует. Две строки на одни сутки — ошибка
 * данных: выбирать наугад нельзя, и расчёт отказывает с перечнем таких строк. Сутки без строки — тоже отказ, с
 * перечнем непокрытых интервалов: половина суммы хуже честного отказа. Если в периоде есть и то и другое,
 * отказ — из-за двух строк на одни сутки. Подряд идущие сутки одной строки складываются в один отрезок; две
 * соседние строки с одинаковой ценой остаются разными отрезками.
 */
public final class ServicePriceCalculator {

    /** Порядок конфликтующих строк в отказе: по началу срока, при равном начале — бессрочная последней. */
    private static final Comparator<PriceLine> BY_VALIDITY = Comparator.comparing(PriceLine::validFrom)
            .thenComparing(PriceLine::validTo, Comparator.nullsLast(Comparator.naturalOrder()));

    private ServicePriceCalculator() {
    }

    /** Строки прайса могут быть любыми строками услуги: те, что на период не приходятся, расчёт пропускает. */
    public static PriceBreakdown calculate(UUID serviceId, StayPeriod period, List<PriceLine> lines) {
        Map<LocalDate, List<PriceLine>> linesByNight = linesByNight(period, lines);
        requireOneLinePerNight(serviceId, linesByNight);
        List<NightRun<List<PriceLine>>> runs = NightRuns.of(period.nights(), linesByNight::get);
        requireEveryNightCovered(serviceId, runs);
        return new PriceBreakdown(runs.stream()
                .map(run -> new PriceSegment(run.from(), run.to(), run.key().getFirst()))
                .toList());
    }

    private static Map<LocalDate, List<PriceLine>> linesByNight(StayPeriod period, List<PriceLine> lines) {
        return period.nights().stream().collect(Collectors.toMap(Function.identity(),
                night -> lines.stream().filter(line -> line.covers(night)).toList()));
    }

    private static void requireOneLinePerNight(UUID serviceId, Map<LocalDate, List<PriceLine>> linesByNight) {
        List<AmbiguousPriceException.PriceRow> overlapping = linesByNight.values().stream()
                .filter(lines -> lines.size() > 1)
                .flatMap(List::stream)
                .distinct()
                .sorted(BY_VALIDITY)
                .map(line -> new AmbiguousPriceException.PriceRow(line.validFrom(), line.validTo()))
                .toList();
        if (!overlapping.isEmpty()) {
            throw new AmbiguousPriceException(serviceId, overlapping);
        }
    }

    private static void requireEveryNightCovered(UUID serviceId, List<NightRun<List<PriceLine>>> runs) {
        List<PriceNotCoveredException.Interval> uncovered = runs.stream()
                .filter(run -> run.key().isEmpty())
                .map(run -> new PriceNotCoveredException.Interval(run.from(), run.to()))
                .toList();
        if (!uncovered.isEmpty()) {
            throw new PriceNotCoveredException(serviceId, uncovered);
        }
    }
}
