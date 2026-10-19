package ru.projectx.datareference.service.lpu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.service.ReadFixtures;
import ru.projectx.datareference.service.rest.ReferenceCalendar;
import ru.projectx.datareference.support.TestApiProperties;

@ExtendWith(MockitoExtension.class)
class LpuOffersTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 14);
    private static final UUID RVL = UUID.fromString("f3000000-0000-4000-8000-000000000001");

    private final Lpu lpu = ReadFixtures.lpu(UUID.fromString("f3000000-0000-4000-8000-000000000002"), false);
    private final LpuContract priceContract = ReadFixtures.lpuContract(lpu, null, false);
    private final AppealType rvl = ReadFixtures.appealType(RVL, "РВЛ и СКЛ");
    private final ContragentService service = ReadFixtures.service(priceContract, RVL);

    @Mock
    private ActiveLpus activeLpus;

    @Mock
    private PriceContractResolver priceContracts;

    @Mock
    private RvlAppealTypes rvlAppealTypes;

    @Mock
    private ContragentServiceRepository contragentServices;

    private LpuOffers offers;

    @BeforeEach
    void setUp() {
        var calendar = new ReferenceCalendar(Clock.fixed(ReadFixtures.NOW, ZoneOffset.UTC), TestApiProperties.defaults());
        offers = new LpuOffers(activeLpus, priceContracts, rvlAppealTypes, contragentServices, calendar);
        when(activeLpus.byId(lpu.id())).thenReturn(lpu);
        when(priceContracts.resolve(lpu.id(), TODAY)).thenReturn(PriceContract.of(priceContract));
    }

    @Test
    void offer_carriesPriceContractAndItsRvlServices() {
        Map<UUID, AppealType> appealTypes = Map.of(RVL, rvl);
        when(rvlAppealTypes.byExternalId()).thenReturn(appealTypes);
        when(contragentServices.findActiveByContractAndAppealTypeIn(priceContract.id(), appealTypes.keySet()))
                .thenReturn(List.of(service));

        LpuOffer offer = offers.of(lpu.id());

        assertThat(offer.lpu()).isSameAs(lpu);
        assertThat(offer.priceContract().contract()).isSameAs(priceContract);
        assertThat(offer.services()).containsExactly(service);
        assertThat(offer.appealTypeOf(service)).isSameAs(rvl);
        assertThat(offer.today()).isEqualTo(TODAY);
        assertThat(offer.response(List.of()).lpu().id()).isEqualTo(lpu.id());
    }

    @Test
    void withoutRvlAppealTypes_hasNoServices_withoutQuery() {
        when(rvlAppealTypes.byExternalId()).thenReturn(Map.of());

        assertThat(offers.of(lpu.id()).services()).isEmpty();
        verify(contragentServices, never()).findActiveByContractAndAppealTypeIn(any(), anyCollection());
    }
}
