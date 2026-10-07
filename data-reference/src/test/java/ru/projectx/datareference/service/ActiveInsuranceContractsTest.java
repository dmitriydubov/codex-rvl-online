package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.entity.InsuranceContract;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.InsuranceContractRepository;

/**
 * По договору страхования, который не действует сегодня, каталог учреждений и поиск полисов отвечают «не найден»,
 * так же как по неизвестному договору: действующих полисов у такого договора нет, и показывать нечего.
 */
@ExtendWith(MockitoExtension.class)
class ActiveInsuranceContractsTest {

    private static final UUID CONTRACT = UUID.fromString("a7000000-0000-4000-8000-000000000001");
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
