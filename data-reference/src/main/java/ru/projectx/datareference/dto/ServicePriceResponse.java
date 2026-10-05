package ru.projectx.datareference.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Стоимость услуги ЛПУ за период проживания для сервиса Заявок: отрезки по строкам прайса и итог.
 *
 * <p>Период и отрезки задаются так же, как период проживания: {@code dateFrom} включительно, {@code dateTo} не
 * включительно, потому что день выезда не оплачивается. Суммы считаются по обеим ценам. Сумма со скидкой пуста
 * у отрезка, у строки прайса которого нет цены со скидкой, и у итога, если такой отрезок есть хотя бы один:
 * складывать скидочные цены с обычными в одном итоге нельзя.
 *
 * @param service услуга, стоимость которой посчитана
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

    /**
     * Итог за период.
     *
     * @param basis по какой сумме считать сооплату: по сумме со скидкой, когда она есть, иначе по сумме без скидки
     */
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
