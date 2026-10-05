package ru.projectx.datareference.service.price;

import java.time.LocalDate;

/**
 * Подряд идущие ночи с одним признаком: с {@code from} включительно до {@code to} не включительно. Так же, как у
 * периода проживания, {@code to} — день, в который последняя ночь уже кончилась.
 */
record NightRun<K>(K key, LocalDate from, LocalDate to) {

    /** Тот же отрезок, продлённый ещё на одну ночь. */
    NightRun<K> withNextNight() {
        return new NightRun<>(key, from, to.plusDays(1));
    }
}
