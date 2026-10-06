package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * У строки прейскуранта нет ключа источника, поэтому свой идентификатор у каждой строки — единственное, что
 * отличает её от соседней с теми же датами и ценами: без него расчёт стоимости слил бы две строки в одну.
 */
class PriceListItemTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final PriceListItem.Row ROW = new PriceListItem.Row(
            "Проживание", "A01", null, new BigDecimal("3500.00"), null, LocalDate.of(2026, 1, 1), null);

    @Test
    void sameRowTwice_givesTwoItemsWithDifferentIds() {
        ContragentService service = mock(ContragentService.class);

        PriceListItem first = PriceListItem.of(service, ROW, NOW);
        PriceListItem second = PriceListItem.of(service, ROW, NOW);

        assertThat(first.id()).isNotNull().isNotEqualTo(second.id());
        assertThat(first.service()).isSameAs(service);
    }
}
