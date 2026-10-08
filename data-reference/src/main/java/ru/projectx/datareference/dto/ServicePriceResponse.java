package ru.projectx.datareference.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Стоимость услуги ЛПУ за период проживания: отрезки по строкам прайса и итог. {@code dateTo} не входит в период,
 * потому что день выезда не оплачивается. Сумма со скидкой пуста у отрезка без цены со скидкой и у итога, если
 * такой отрезок есть: складывать скидочные цены с обычными в одном итоге нельзя.
 */
public record ServicePriceResponse(
        NamedRef service, LocalDate dateFrom, LocalDate dateTo, List<Segment> segments, Total total) {

    public ServicePriceResponse {
        segments = List.copyOf(segments);
    }

    /** Подряд идущие ночи, которые оплачиваются по одной строке прайса. */
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

    /** Какая сумма итога основная для расчёта сооплаты. */
    public enum Basis {
        /** Сумма со скидкой: цена со скидкой есть у всех отрезков. */
        DISCOUNT,
        /** Сумма без скидки: хотя бы у одного отрезка цены со скидкой нет. */
        BASE
    }
}
