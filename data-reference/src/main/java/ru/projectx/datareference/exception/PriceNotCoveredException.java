package ru.projectx.datareference.exception;

import java.io.Serial;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * В периоде проживания есть сутки, на которые у услуги нет ни одной строки прайса. Частичную сумму сервис не
 * отдаёт: половина суммы хуже честного отказа. Он отвечает 422 и перечисляет непокрытые интервалы, а по ним прайс
 * дополняют в 1С:УСК.
 *
 * <p>Отдельное исключение, а не ещё один случай «данных не хватает для ответа» про договор ЛПУ: здесь виновата
 * услуга, а не учреждение, и в ответ уходит перечень интервалов, а не пустой список.
 */
public final class PriceNotCoveredException extends ServicePriceListException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String PRICE_NOT_COVERED = "PRICE_NOT_COVERED";

    private final transient List<Interval> intervals;

    public PriceNotCoveredException(UUID serviceId, List<Interval> intervals) {
        super(PRICE_NOT_COVERED, "у услуги " + serviceId + " в периоде есть сутки без строки прайса", serviceId);
        this.intervals = List.copyOf(intervals);
    }

    public List<Interval> intervals() {
        return intervals;
    }

    /** Непокрытые сутки подряд: с {@code dateFrom} включительно до {@code dateTo} не включительно, как период. */
    public record Interval(LocalDate dateFrom, LocalDate dateTo) {
    }
}
