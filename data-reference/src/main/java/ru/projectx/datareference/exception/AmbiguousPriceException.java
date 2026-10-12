package ru.projectx.datareference.exception;

import java.io.Serial;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public final class AmbiguousPriceException extends ServicePriceListException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String AMBIGUOUS_PRICE = "AMBIGUOUS_PRICE";

    private final transient List<PriceRow> rows;

    public AmbiguousPriceException(UUID serviceId, List<PriceRow> rows) {
        super(AMBIGUOUS_PRICE, "у услуги " + serviceId + " на одни сутки периода действует больше одной строки прайса",
                serviceId);
        this.rows = List.copyOf(rows);
    }

    public List<PriceRow> rows() {
        return rows;
    }

    public record PriceRow(LocalDate validFrom, @Nullable LocalDate validTo) {
    }
}
