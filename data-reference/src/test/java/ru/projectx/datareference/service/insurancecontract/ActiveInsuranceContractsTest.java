package ru.projectx.datareference.service.insurancecontract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.entity.catalog.InsuranceContract;
import ru.projectx.datareference.exception.AmbiguousReferenceException;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.InsuranceContractRepository;
import ru.projectx.datareference.service.ReadFixtures;

@ExtendWith(MockitoExtension.class)
class ActiveInsuranceContractsTest {

    private static final UUID CONTRACT = UUID.fromString("a7000000-0000-4000-8000-000000000001");
    private static final UUID INSURER = UUID.fromString("a7000000-0000-4000-8000-000000000002");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 14);

    @Mock
    private InsuranceContractRepository insuranceContracts;

    private ActiveInsuranceContracts activeContracts;

    @BeforeEach
    void setUp() {
        activeContracts = new ActiveInsuranceContracts(insuranceContracts);
    }

    @Test
    void requireActive_contractInItsTerm_passes() {
        known(TODAY, TODAY, false);

        assertThatCode(() -> activeContracts.requireActive(CONTRACT, TODAY)).doesNotThrowAnyException();
    }

    @Test
    void requireActive_unknownContract_isNotFound() {
        when(insuranceContracts.findByExternalId(CONTRACT)).thenReturn(Optional.empty());

        assertNotFound();
    }

    @Test
    void requireActive_expiredContract_isNotFound() {
        known(TODAY.minusYears(1), TODAY.minusDays(1), false);

        assertNotFound();
    }

    @Test
    void requireActive_contractNotStartedYet_isNotFound() {
        known(TODAY.plusDays(1), null, false);

        assertNotFound();
    }

    @Test
    void requireActive_archivedContract_isNotFound() {
        known(TODAY.minusYears(1), null, true);

        assertNotFound();
    }

    @Test
    void soleActiveOf_insurerWithOneActiveAmongInactive_isThatContract() {
        InsuranceContract active = contract(TODAY, TODAY, false);
        insurerHas(contract(TODAY.minusYears(1), TODAY.minusDays(1), false), active,
                contract(TODAY.plusDays(1), null, false), contract(TODAY.minusYears(1), null, true));

        assertThat(activeContracts.soleActiveOf(INSURER, TODAY)).isSameAs(active);
    }

    @Test
    void soleActiveOf_insurerWithoutContracts_isNotFoundNamingInsurer() {
        insurerHas();

        assertNoActiveOfInsurer();
    }

    @Test
    void soleActiveOf_insurerWithOnlyInactiveContracts_isNotFoundNamingInsurer() {
        insurerHas(contract(TODAY.minusYears(1), TODAY.minusDays(1), false), contract(TODAY.plusDays(1), null, false),
                contract(TODAY.minusYears(1), null, true));

        assertNoActiveOfInsurer();
    }

    @Test
    void soleActiveOf_insurerWithTwoActive_isAmbiguousListingBothAndNotInactive() {
        InsuranceContract first = contract(TODAY.minusYears(1), null, false);
        InsuranceContract second = contract(TODAY, TODAY, false);
        insurerHas(first, contract(TODAY.minusYears(2), TODAY.minusYears(1), false), second);

        assertThatThrownBy(() -> activeContracts.soleActiveOf(INSURER, TODAY))
                .isInstanceOfSatisfying(AmbiguousReferenceException.class, ambiguous -> {
                    assertThat(ambiguous.code()).isEqualTo("AMBIGUOUS_INSURANCE_CONTRACT");
                    assertThat(ambiguous.getMessage()).contains(INSURER.toString());
                    assertThat(ambiguous.candidateIds()).containsExactly(first.id(), second.id());
                });
    }

    private void insurerHas(InsuranceContract... contracts) {
        when(insuranceContracts.findAllByExternalInsurerId(INSURER)).thenReturn(List.of(contracts));
    }

    private static InsuranceContract contract(LocalDate begin, @Nullable LocalDate end, boolean archived) {
        return InsuranceContract.create(UUID.randomUUID(),
                new InsuranceContract.Details("ДМС-0001", begin, end, INSURER, archived), ReadFixtures.NOW);
    }

    private void assertNoActiveOfInsurer() {
        assertThatThrownBy(() -> activeContracts.soleActiveOf(INSURER, TODAY))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class, notFound -> {
                    assertThat(notFound.code()).isEqualTo("INSURANCE_CONTRACT_NOT_FOUND");
                    assertThat(notFound.getMessage()).contains(INSURER.toString());
                });
    }

    private void known(LocalDate begin, @Nullable LocalDate end, boolean archived) {
        InsuranceContract contract = InsuranceContract.create(CONTRACT,
                new InsuranceContract.Details("ДМС-0001", begin, end, null, archived), ReadFixtures.NOW);
        when(insuranceContracts.findByExternalId(CONTRACT)).thenReturn(Optional.of(contract));
    }

    private void assertNotFound() {
        assertThatThrownBy(() -> activeContracts.requireActive(CONTRACT, TODAY))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class, notFound -> {
                    assertThat(notFound.code()).isEqualTo("INSURANCE_CONTRACT_NOT_FOUND");
                    assertThat(notFound.getMessage()).contains(CONTRACT.toString());
                });
    }
}
