package ru.projectx.datareference.service.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.dto.NamedRef;
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.entity.PriceListItem;
import ru.projectx.datareference.exception.IncompleteReferenceException;
import ru.projectx.datareference.exception.MissingReference;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.PriceListItemRepository;
import ru.projectx.datareference.service.ReadFixtures;
import ru.projectx.datareference.service.lpu.LpuOffer;
import ru.projectx.datareference.service.lpu.LpuOffers;
import ru.projectx.datareference.service.lpu.PriceContract;

@ExtendWith(MockitoExtension.class)
class ServicePriceLookupTest {

    private static final UUID RVL = UUID.fromString("5e000000-0000-4000-8000-000000000003");
    private static final LocalDate APRIL_10 = LocalDate.of(2026, 4, 10);
    private static final StayPeriod PERIOD = new StayPeriod(APRIL_10, APRIL_10.plusDays(2));

    private final Lpu lpu = ReadFixtures.lpu(UUID.randomUUID(), false);
    private final LpuContract priceContract = ReadFixtures.lpuContract(lpu, null, false);
    private final AppealType rvl = ReadFixtures.appealType(RVL, "РВЛ и СКЛ");
    private final ContragentService service = ReadFixtures.service(priceContract, RVL);

    @Mock
    private ContragentServiceRepository contragentServices;

    @Mock
    private LpuOffers offers;

    @Mock
    private PriceListItemRepository priceListItems;

    private ServicePriceLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new ServicePriceLookup(contragentServices, offers, priceListItems);
    }

    @Test
    void offeredService_isNamedAndCarriesRowsReadForWholePeriod() {
        PriceListItem item = ReadFixtures.price(service, "3500.00");
        when(contragentServices.findById(service.id())).thenReturn(Optional.of(service));
        when(offers.of(lpu.id())).thenReturn(offer(List.of(service)));
        when(priceListItems.findWithinPeriodByServiceId(service.id(), PERIOD.checkIn(), PERIOD.checkOut()))
                .thenReturn(List.of(item));

        PricedService priced = lookup.source(service.id(), PERIOD);

        assertThat(priced.service()).isEqualTo(new NamedRef(service.id(), service.externalId(), service.name()));
        assertThat(priced.lines()).containsExactly(PriceLine.of(item));
    }

    @Test
    void archivedServiceOfAnotherContract_isNotFound_notDataError() {
        LpuContract mainContract = ReadFixtures.lpuContract(lpu, null, false);
        ContragentService archived = ContragentService.create(UUID.randomUUID(), mainContract,
                new ContragentService.Details(RVL, "У-1", "Размещение", true), ReadFixtures.NOW);
        when(contragentServices.findById(archived.id())).thenReturn(Optional.of(archived));
        when(offers.of(lpu.id())).thenReturn(offer(List.of(service)));

        assertThatThrownBy(() -> lookup.source(archived.id(), PERIOD))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class,
                        notFound -> assertThat(notFound.code()).isEqualTo("SERVICE_NOT_FOUND"));
        verifyNoInteractions(priceListItems);
    }

    @Test
    void unknownService_isNotFound_beforeAskingForLpu() {
        UUID unknown = UUID.randomUUID();
        when(contragentServices.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> lookup.source(unknown, PERIOD))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class,
                        notFound -> assertThat(notFound.code()).isEqualTo("SERVICE_NOT_FOUND"));
        verifyNoInteractions(offers, priceListItems);
    }

    @Test
    void lpuRefusal_comesBeforeServiceChecks() {
        when(contragentServices.findById(service.id())).thenReturn(Optional.of(service));
        when(offers.of(lpu.id())).thenThrow(MissingReference.LPU.notFound(lpu.id()));

        assertThatThrownBy(() -> lookup.source(service.id(), PERIOD))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class,
                        notFound -> assertThat(notFound.code()).isEqualTo("LPU_NOT_FOUND"));
        verifyNoInteractions(priceListItems);
    }

    @Test
    void serviceOfAnotherContract_isNoActivePriceContract_namingLpu() {
        LpuContract mainContract = ReadFixtures.lpuContract(lpu, null, false);
        ContragentService mainContractService = ReadFixtures.service(mainContract, RVL);
        when(contragentServices.findById(mainContractService.id())).thenReturn(Optional.of(mainContractService));
        when(offers.of(lpu.id())).thenReturn(offer(List.of(service)));

        assertThatThrownBy(() -> lookup.source(mainContractService.id(), PERIOD))
                .isInstanceOfSatisfying(IncompleteReferenceException.class, incomplete -> {
                    assertThat(incomplete.code()).isEqualTo("NO_ACTIVE_PRICE_CONTRACT");
                    assertThat(incomplete.lpuId()).isEqualTo(lpu.id());
                });
        verifyNoInteractions(priceListItems);
    }

    @Test
    void serviceOfPriceContractMissingFromOffer_isNotFound() {
        when(contragentServices.findById(service.id())).thenReturn(Optional.of(service));
        when(offers.of(lpu.id())).thenReturn(offer(List.of()));

        assertThatThrownBy(() -> lookup.source(service.id(), PERIOD))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class,
                        notFound -> assertThat(notFound.code()).isEqualTo("SERVICE_NOT_FOUND"));
        verifyNoInteractions(priceListItems);
    }

    @Test
    void serviceOfPriceContractOutsideNonEmptyOffer_isNotFound() {
        ContragentService offered = ReadFixtures.service(priceContract, RVL);
        when(contragentServices.findById(service.id())).thenReturn(Optional.of(service));
        when(offers.of(lpu.id())).thenReturn(offer(List.of(offered)));

        assertThatThrownBy(() -> lookup.source(service.id(), PERIOD))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class,
                        notFound -> assertThat(notFound.code()).isEqualTo("SERVICE_NOT_FOUND"));
        verifyNoInteractions(priceListItems);
    }

    private LpuOffer offer(List<ContragentService> services) {
        return new LpuOffer(lpu, PriceContract.of(priceContract), Map.of(RVL, rvl), services, APRIL_10);
    }
}
