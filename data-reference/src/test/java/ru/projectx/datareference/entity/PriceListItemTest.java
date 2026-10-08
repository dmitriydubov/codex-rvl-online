package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * У строки прейскуранта нет ключа источника, а строки одной услуги могут совпадать датами и ценами.
 * Поэтому сервис сам даёт каждой свой идентификатор, а значения хранит как пришли: по ним выбирается цена на дату.
 */
class PriceListItemTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final PriceListItem.Row ROW = new PriceListItem.Row(
            "Проживание", "A01", null, new BigDecimal("3500.00"), null, LocalDate.of(2026, 1, 1), null);

    @Test
    void sameRowTwice_givesTwoItemsWithDifferentIds() {
        ContragentService service = service();

        PriceListItem first = PriceListItem.of(service, ROW, NOW);
        PriceListItem second = PriceListItem.of(service, ROW, NOW);

        assertThat(first.id()).isNotNull().isNotEqualTo(second.id());
        assertThat(first.service()).isSameAs(service);
    }

    @Test
    void of_keepsEveryValueOfTheRow() {
        var row = new PriceListItem.Row("Проживание", "A01", "PF-01", new BigDecimal("3500.00"),
                new BigDecimal("3150.00"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 30));

        PriceListItem item = PriceListItem.of(service(), row, NOW);

        assertThat(item.serviceName()).isEqualTo("Проживание");
        assertThat(item.serviceCode()).isEqualTo("A01");
        assertThat(item.serviceCodePf()).isEqualTo("PF-01");
        assertThat(item.price()).isEqualByComparingTo("3500.00");
        assertThat(item.priceWithDiscount()).isEqualByComparingTo("3150.00");
        assertThat(item.validFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(item.validTo()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    private static ContragentService service() {
        Lpu lpu = Lpu.create(UUID.randomUUID(),
                new Lpu.Details("Санаторий", null, null, null, null, null, false), NOW);
        LpuContract contract = LpuContract.create(UUID.randomUUID(), lpu, new LpuContract.Details(
                "Д-1", null, null, false, false, LocalDate.of(2026, 1, 1), null, false), NOW);
        return ContragentService.create(UUID.randomUUID(), contract,
                new ContragentService.Details(null, "A01", "Проживание", false), NOW);
    }
}
