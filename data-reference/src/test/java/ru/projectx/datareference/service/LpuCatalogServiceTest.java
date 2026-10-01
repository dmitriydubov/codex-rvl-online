package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.dto.LpuCatalogResponse;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.repository.PolicyRepository;
import ru.projectx.datareference.service.PriceContractResolver.Resolution;
import ru.projectx.datareference.support.TestApiProperties;

/**
 * R-01: учреждения программ РВЛ действующих полисов по видам обращения РВЛ; с договором — в выдаче,
 * с неоднозначным договором — в {@code skipped}, без договора — нигде.
 */
@ExtendWith(MockitoExtension.class)
class LpuCatalogServiceTest {

    private static final UUID CONTRACT = UUID.fromString("f0000000-0000-4000-8000-000000000001");
    private static final UUID PROGRAM = UUID.fromString("f0000000-0000-4000-8000-000000000002");
    private static final UUID APPEAL_TYPE = UUID.fromString("f0000000-0000-4000-8000-000000000003");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 14);

    private final Lpu found = ReadFixtures.lpu(UUID.fromString("f0000000-0000-4000-8000-000000000011"), false);
    private final Lpu ambiguous = ReadFixtures.lpu(UUID.fromString("f0000000-0000-4000-8000-000000000012"), false);
    private final Lpu withoutContract = ReadFixtures.lpu(UUID.fromString("f0000000-0000-4000-8000-000000000013"), false);

    @Mock
    private InsuranceContractRepository insuranceContracts;

    @Mock
    private PolicyRepository policies;

    @Mock
    private LpuRepository lpus;

    @Mock
    private PriceContractResolver priceContracts;

    @Mock
    private RvlAppealTypes rvlAppealTypes;

    @Mock
    private InsuranceContract contract;

    private LpuCatalogService service;

    @BeforeEach
    void setUp() {
        var calendar = new ReferenceCalendar(Clock.fixed(ReadFixtures.NOW, ZoneOffset.UTC), TestApiProperties.defaults());
        service = new LpuCatalogService(insuranceContracts, policies, lpus, priceContracts, rvlAppealTypes, calendar);
    }

    @Test
    void catalog_listsLpuWithPriceContract_skipsAmbiguous_dropsWithoutContract() {
        knownContract();
        when(policies.findRvlProgramExternalIdsOfActivePolicies(CONTRACT, TODAY)).thenReturn(List.of(PROGRAM));
        when(rvlAppealTypes.externalIds()).thenReturn(Set.of(APPEAL_TYPE));
        when(lpus.findRvlCatalog(List.of(PROGRAM), Set.of(APPEAL_TYPE)))
                .thenReturn(List.of(found, ambiguous, withoutContract));
        when(priceContracts.resolveAll(List.of(found.id(), ambiguous.id(), withoutContract.id()), TODAY)).thenReturn(Map.of(
                found.id(), new Resolution.Found(PriceContract.of(ReadFixtures.lpuContract(found, null, false))),
                ambiguous.id(), new Resolution.Ambiguous(List.of(UUID.randomUUID(), UUID.randomUUID())),
                withoutContract.id(), new Resolution.None()));

        LpuCatalogResponse catalog = service.catalog(CONTRACT);

        assertThat(catalog.items()).extracting(LpuCatalogResponse.Item::id).containsExactly(found.id());
        assertThat(catalog.skipped()).containsExactly(
                new LpuCatalogResponse.Skipped(ambiguous.id(), "AMBIGUOUS_PRICE_CONTRACT"));
    }

    @Test
    void catalog_withoutRvlPrograms_isEmpty_withoutLpuQuery() {
        knownContract();
        when(policies.findRvlProgramExternalIdsOfActivePolicies(CONTRACT, TODAY)).thenReturn(List.of());
        when(rvlAppealTypes.externalIds()).thenReturn(Set.of(APPEAL_TYPE));

        assertThat(service.catalog(CONTRACT).items()).isEmpty();
        verify(lpus, never()).findRvlCatalog(anyList(), anyCollection());
    }

    @Test
    void catalog_withoutRvlAppealTypes_isEmpty_withoutLpuQuery() {
        knownContract();
        when(policies.findRvlProgramExternalIdsOfActivePolicies(CONTRACT, TODAY)).thenReturn(List.of(PROGRAM));
        when(rvlAppealTypes.externalIds()).thenReturn(Set.of());
        when(priceContracts.resolveAll(List.of(), TODAY)).thenReturn(Map.of());

        assertThat(service.catalog(CONTRACT).skipped()).isEmpty();
        verify(lpus, never()).findRvlCatalog(anyList(), anyCollection());
    }

    @Test
    void catalog_unknownOrArchivedContract_isNotFound() {
        when(insuranceContracts.findByExternalIdAndArchiveFalse(CONTRACT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.catalog(CONTRACT))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class,
                        notFound -> assertThat(notFound.code()).isEqualTo("INSURANCE_CONTRACT_NOT_FOUND"));
        verify(priceContracts, never()).resolveAll(anyCollection(), any());
    }

    private void knownContract() {
        when(insuranceContracts.findByExternalIdAndArchiveFalse(CONTRACT)).thenReturn(Optional.of(contract));
    }
}
