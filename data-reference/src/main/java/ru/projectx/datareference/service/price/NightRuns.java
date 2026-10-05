package ru.projectx.datareference.service.price;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Склеивает подряд идущие ночи с одинаковым признаком в отрезки. Так из суток периода получаются и отрезки
 * расчёта (признак — строка прайса), и непокрытые интервалы (признак — «строки нет»).
 */
final class NightRuns {

    private NightRuns() {
    }

    /**
     * Ночи должны идти подряд и по порядку, как их отдаёт {@link StayPeriod#nights()}. Тогда новый отрезок
     * начинается только там, где меняется признак.
     */
    static <K> List<NightRun<K>> of(List<LocalDate> nights, Function<LocalDate, K> keyOf) {
        List<NightRun<K>> runs = new ArrayList<>();
        for (LocalDate night : nights) {
            K key = keyOf.apply(night);
            if (lastRunHasKey(runs, key)) {
                runs.set(runs.size() - 1, runs.getLast().withNextNight());
            } else {
                runs.add(new NightRun<>(key, night, night.plusDays(1)));
            }
        }
        return runs;
    }

    private static <K> boolean lastRunHasKey(List<NightRun<K>> runs, K key) {
        return !runs.isEmpty() && runs.getLast().key().equals(key);
    }
}
