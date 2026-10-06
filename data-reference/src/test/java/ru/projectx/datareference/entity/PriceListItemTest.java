package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * У строки прейскуранта нет ключа источника, и идентификатор ей присваивает сам сервис при создании. Строки одной
 * услуги могут совпадать датами и ценами, поэтому у каждой должен быть свой идентификатор: без него строка не
 * сохранится, а повторённый идентификатор нарушит первичный ключ таблицы цен.
 */
class PriceListItemTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final PriceListItem.Row ROW = new PriceListItem.Row(
            "Проживание", "A01", null, new BigDecimal("3500.00"), null, LocalDate.of(2026, 1, 1), null);

    @Test
    void sameRowTwice_givesTwoItemsWithDifferentIds() {
        Lpu lpu = Lpu.create(UUID.randomUUID(),
                new Lpu.Details("Санаторий", null, null, null, null, null, false), NOW);
        LpuContract contract = LpuContract.create(UUID.randomUUID(), lpu, new LpuContract.Details(
                "Д-1", null, null, false, false, LocalDate.of(2026, 1, 1), null, false), NOW);
        ContragentService service = ContragentService.create(UUID.randomUUID(), contract,
                new ContragentService.Details(null, "A01", "Проживание", false), NOW);

        PriceListItem first = PriceListItem.of(service, ROW, NOW);
        PriceListItem second = PriceListItem.of(service, ROW, NOW);

        assertThat(first.id()).isNotNull().isNotEqualTo(second.id());
        assertThat(first.service()).isSameAs(service);
    }
}
