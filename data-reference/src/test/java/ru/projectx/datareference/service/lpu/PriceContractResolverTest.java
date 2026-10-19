package ru.projectx.datareference.service.lpu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.dto.PriceContractRef;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.LpuContract;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.IncompleteReferenceException;
import ru.projectx.datareference.repository.LpuContractRepository;
import ru.projectx.datareference.service.ReadFixtures;
import ru.projectx.datareference.service.lpu.PriceContractResolver.Resolution;

@ExtendWith(MockitoExtension.class)
class PriceContractResolverTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 14);
    private static final UUID MAIN_EXTERNAL_ID = UUID.fromString("e0000000-0000-4000-8000-000000000001");

    private final Lpu lpu = ReadFixtures.lpu(UUID.fromString("e0000000-0000-4000-8000-000000000002"), false);
    private final LpuContract main = ReadFixtures.lpuContract(lpu, null, false);
    private final LpuContract supplementWithOwnPrices = ReadFixtures.lpuContract(lpu, MAIN_EXTERNAL_ID, false);
    private final LpuContract supplementWithMainPrices = ReadFixtures.lpuContract(lpu, MAIN_EXTERNAL_ID, true);

    @Mock
    private LpuContractRepository lpuContracts;

    @InjectMocks
    private PriceContractResolver resolver;

    @Test
    void onlyMain_isMain() {
        active(main, supplementWithMainPrices);

        PriceContract found = resolver.resolve(lpu.id(), TODAY);

        assertThat(found.contract()).isSameAs(main);
        assertThat(found.kind()).isEqualTo(PriceContractRef.Kind.MAIN);
    }

    @Test
    void supplementWithOwnPrices_winsOverMain() {
        active(main, supplementWithOwnPrices, supplementWithMainPrices);

        PriceContract found = resolver.resolve(lpu.id(), TODAY);

        assertThat(found.contract()).isSameAs(supplementWithOwnPrices);
        assertThat(found.kind()).isEqualTo(PriceContractRef.Kind.DOPS);
    }

    @Test
    void twoSupplementsWithOwnPrices_areAmbiguous_namingBoth() {
        LpuContract another = ReadFixtures.lpuContract(lpu, MAIN_EXTERNAL_ID, false);
        active(main, supplementWithOwnPrices, another);

        assertThatThrownBy(() -> resolver.resolve(lpu.id(), TODAY))
                .isInstanceOfSatisfying(AmbiguousReferenceException.class,
                        ambiguous -> assertThat(ambiguous.candidateIds()).hasSize(2));
    }

    @Test
    void twoMains_areAmbiguous() {
        active(main, ReadFixtures.lpuContract(lpu, null, true));

        assertThat(resolver.resolveAll(List.of(lpu.id()), TODAY).get(lpu.id()))
                .isInstanceOfSatisfying(Resolution.Ambiguous.class,
                        ambiguous -> assertThat(ambiguous.lpuContractIds()).hasSize(2));
    }

    @Test
    void onlySupplementWithMainPrices_isNone() {
        active(supplementWithMainPrices);

        assertThatThrownBy(() -> resolver.resolve(lpu.id(), TODAY))
                .isInstanceOfSatisfying(IncompleteReferenceException.class,
                        incomplete -> assertThat(incomplete.code()).isEqualTo("NO_ACTIVE_PRICE_CONTRACT"));
    }

    @Test
    void resolveAll_givesOutcomePerRequestedLpu_includingLpuWithoutContracts() {
        UUID withoutContracts = UUID.fromString("e0000000-0000-4000-8000-000000000003");
        when(lpuContracts.findActiveByLpuIdIn(List.of(lpu.id(), withoutContracts, lpu.id()), TODAY))
                .thenReturn(List.of(main));

        Map<UUID, Resolution> outcomes = resolver.resolveAll(List.of(lpu.id(), withoutContracts, lpu.id()), TODAY);

        assertThat(outcomes).containsOnlyKeys(lpu.id(), withoutContracts);
        assertThat(outcomes.get(lpu.id())).isEqualTo(new Resolution.Found(PriceContract.of(main)));
        assertThat(outcomes.get(withoutContracts)).isInstanceOf(Resolution.None.class);
    }

    @Test
    void resolveAll_withoutLpu_doesNotQuery() {
        assertThat(resolver.resolveAll(List.of(), TODAY)).isEmpty();
        verifyNoInteractions(lpuContracts);
    }

    private void active(LpuContract... contracts) {
        when(lpuContracts.findActiveByLpuIdIn(List.of(lpu.id()), TODAY)).thenReturn(List.of(contracts));
    }
}
