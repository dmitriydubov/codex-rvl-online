package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.dto.LpuCardResponse;
import ru.projectx.datareference.dto.PriceContractRef;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.support.TestApiProperties;

/**
 * Карточка ЛПУ (лечебно-профилактического учреждения): действующее учреждение и его прайсовый договор
 * на сегодняшний день контура.
 */
@ExtendWith(MockitoExtension.class)
class LpuCardServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 14);

    private final Lpu lpu = ReadFixtures.lpu(UUID.fromString("f1000000-0000-4000-8000-000000000001"), false);
    private final LpuContract priceContract = ReadFixtures.lpuContract(lpu, null, false);

    @Mock
    private ActiveLpus activeLpus;

    @Mock
    private PriceContractResolver priceContracts;

    private LpuCardService service;

    @BeforeEach
    void setUp() {
        var calendar = new ReferenceCalendar(Clock.fixed(ReadFixtures.NOW, ZoneOffset.UTC), TestApiProperties.defaults());
        service = new LpuCardService(activeLpus, priceContracts, calendar);
    }

    @Test
    void card_carriesLpuAndItsPriceContract() {
        when(activeLpus.byId(lpu.id())).thenReturn(lpu);
        when(priceContracts.resolve(lpu.id(), TODAY)).thenReturn(PriceContract.of(priceContract));

        LpuCardResponse card = service.card(lpu.id());

        assertThat(card.id()).isEqualTo(lpu.id());
        assertThat(card.priceContract().id()).isEqualTo(priceContract.id());
        assertThat(card.priceContract().kind()).isEqualTo(PriceContractRef.Kind.MAIN);
    }
}
