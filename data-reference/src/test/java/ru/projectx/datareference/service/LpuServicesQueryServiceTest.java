package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.dto.LpuServicesResponse;
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.repository.PriceListItemRepository;

/**
 * R-03: без цен — поля {@code prices} нет и цены не читаются; с ценами — у каждой услуги перечень,
 * у услуги без актуальных цен он пуст.
 */
@ExtendWith(MockitoExtension.class)
class LpuServicesQueryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 14);
    private static final UUID RVL = UUID.fromString("f3000000-0000-4000-8000-000000000001");

    private final Lpu lpu = ReadFixtures.lpu(UUID.fromString("f3000000-0000-4000-8000-000000000002"), false);
    private final LpuContract priceContract = ReadFixtures.lpuContract(lpu, null, false);
    private final AppealType rvl = ReadFixtures.appealType(RVL, "РВЛ и СКЛ");
    private final ContragentService priced = ReadFixtures.service(priceContract, RVL);
    private final ContragentService unpriced = ReadFixtures.service(priceContract, RVL);

    @Mock
    private LpuOffers offers;

    @Mock
    private PriceListItemRepository priceListItems;

    @InjectMocks
    private LpuServicesQueryService service;

    @Test
    void withoutPrices_listsServicesWithAppealType_withoutPrices_andDoesNotReadPrices() {
        offer(List.of(priced, unpriced));

        LpuServicesResponse response = service.withoutPrices(lpu.id());

        assertThat(response.lpu().id()).isEqualTo(lpu.id());
        assertThat(response.services()).extracting(LpuServicesResponse.Service::id)
                .containsExactly(priced.id(), unpriced.id());
        assertThat(response.services()).allSatisfy(row -> {
            assertThat(row.prices()).isNull();
            assertThat(row.appealType().externalId()).isEqualTo(RVL);
        });
        verify(priceListItems, never()).findCurrentByServiceIdIn(anyList(), any());
    }

    @Test
    void withPrices_givesEveryServiceItsCurrentPrices_emptyWhenNone() {
        offer(List.of(priced, unpriced));
        when(priceListItems.findCurrentByServiceIdIn(List.of(priced.id(), unpriced.id()), TODAY))
                .thenReturn(List.of(ReadFixtures.price(priced, "15000.00")));

        LpuServicesResponse response = service.withPrices(lpu.id());

        assertThat(response.services().get(0).prices()).singleElement()
                .extracting(LpuServicesResponse.Price::price).isEqualTo(new BigDecimal("15000.00"));
        assertThat(response.services().get(1).prices()).isEmpty();
    }

    @Test
    void withPrices_withoutServices_doesNotReadPrices() {
        offer(List.of());

        assertThat(service.withPrices(lpu.id()).services()).isEmpty();
        verify(priceListItems, never()).findCurrentByServiceIdIn(anyList(), any());
    }

    private void offer(List<ContragentService> services) {
        when(offers.of(lpu.id())).thenReturn(
                new LpuOffer(lpu, PriceContract.of(priceContract), Map.of(RVL, rvl), services, TODAY));
    }
}
