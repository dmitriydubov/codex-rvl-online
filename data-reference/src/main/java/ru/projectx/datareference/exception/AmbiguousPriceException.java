package ru.projectx.datareference.exception;

import java.io.Serial;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * На одни сутки периода действует больше одной строки прайса услуги, а выбирать цену наугад нельзя: сервис
 * отвечает 409 и перечисляет конфликтующие строки. Строку называют её сроки действия, потому что своего
 * идентификатора в 1С у неё нет, а наш меняется при каждой загрузке прайса.
 */
public final class AmbiguousPriceException extends ServicePriceListException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String AMBIGUOUS_PRICE = "AMBIGUOUS_PRICE";

    private final transient List<PriceRow> rows;

    /** Строки — в том порядке, в каком их надо показать: по началу срока действия. */
    public AmbiguousPriceException(UUID serviceId, List<PriceRow> rows) {
        super(AMBIGUOUS_PRICE, "у услуги " + serviceId + " на одни сутки периода действует больше одной строки прайса",
                serviceId);
        this.rows = List.copyOf(rows);
    }

    public List<PriceRow> rows() {
        return rows;
    }

    /** Конфликтующая строка прайса: срок действия с {@code validFrom} по {@code validTo} включительно. */
    public record PriceRow(LocalDate validFrom, @Nullable LocalDate validTo) {
    }
}
