package ru.projectx.datareference.service.price;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.PriceListItem;

class PriceLineTest {

    private static final LocalDate FROM = LocalDate.of(2026, 4, 1);
    private static final LocalDate TO = LocalDate.of(2026, 4, 30);

    @Test
    void of_takesDatesAndBothPricesFromStoredRow() {
        Instant now = Instant.parse("2026-03-14T09:15:00Z");
        Lpu lpu = Lpu.create(UUID.randomUUID(), new Lpu.Details("Санаторий", null, null, null, null, null, false), now);
        LpuContract contract = LpuContract.create(UUID.randomUUID(), lpu,
                new LpuContract.Details("Д-1", null, null, false, false, FROM, null, false), now);
        ContragentService service = ContragentService.create(UUID.randomUUID(), contract,
                new ContragentService.Details(null, "У-1", "Размещение", false), now);
        PriceListItem item = PriceListItem.of(service, new PriceListItem.Row(null, null, null,
                new BigDecimal("12000.00"), new BigDecimal("11400.00"), FROM, TO), now);

        PriceLine line = PriceLine.of(item);

        assertThat(line).isEqualTo(new PriceLine(item.id(), FROM, TO,
                new BigDecimal("12000.00"), new BigDecimal("11400.00")));
    }
}
