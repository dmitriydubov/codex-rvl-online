package ru.projectx.datareference.service.insurancecontract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.dto.InsuranceContractResponse;
import ru.projectx.datareference.entity.catalog.InsuranceContract;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.catalog.InsuranceContractRepository;
import ru.projectx.datareference.repository.catalog.InsurerRepository;
import ru.projectx.datareference.service.ReadFixtures;
import ru.projectx.datareference.service.rest.ReferenceCalendar;

@ExtendWith(MockitoExtension.class)
class InsuranceContractQueryServiceTest {

    private static final UUID ID = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID EXTERNAL_ID = UUID.fromString("c0000000-0000-4000-8000-000000000002");
    private static final UUID INSURER_EXTERNAL_ID = UUID.fromString("c0000000-0000-4000-8000-000000000003");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);

    @Mock
    private InsuranceContractRepository insuranceContracts;

    @Mock
    private InsurerRepository insurers;

    @Mock
    private ActiveInsuranceContracts activeContracts;

    @Mock
    private ReferenceCalendar calendar;

    @InjectMocks
    private InsuranceContractQueryService service;

    @Test
    void byId_carriesInsurerFoundByGuid() {
        when(insuranceContracts.findById(ID)).thenReturn(Optional.of(contract(INSURER_EXTERNAL_ID)));
        when(insurers.findByExternalId(INSURER_EXTERNAL_ID))
                .thenReturn(Optional.of(ReadFixtures.insurer(INSURER_EXTERNAL_ID)));

        InsuranceContractResponse response = service.byId(ID);

        assertThat(response.externalId()).isEqualTo(EXTERNAL_ID);
        assertThat(response.insurer()).isNotNull()
                .extracting(InsuranceContractResponse.Organization::externalId).isEqualTo(INSURER_EXTERNAL_ID);
    }

    @Test
    void byExternalId_insurerMissingInReference_isEmptyInsurer() {
        when(insuranceContracts.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.of(contract(INSURER_EXTERNAL_ID)));
        when(insurers.findByExternalId(INSURER_EXTERNAL_ID)).thenReturn(Optional.empty());

        assertThat(service.byExternalId(EXTERNAL_ID).insurer()).isNull();
    }

    @Test
    void contractWithoutInsurerGuid_doesNotLookForInsurer() {
        when(insuranceContracts.findById(ID)).thenReturn(Optional.of(contract(null)));

        assertThat(service.byId(ID).insurer()).isNull();
        verifyNoInteractions(insurers);
    }

    @Test
    void byId_unknown_isInsuranceContractNotFound() {
        when(insuranceContracts.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.byId(ID))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class, notFound -> {
                    assertThat(notFound.code()).isEqualTo("INSURANCE_CONTRACT_NOT_FOUND");
                    assertThat(notFound.getMessage()).contains(ID.toString());
                });
    }

    @Test
    void byExternalId_unknown_isInsuranceContractNotFound_namingExternalId() {
        when(insuranceContracts.findByExternalId(EXTERNAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.byExternalId(EXTERNAL_ID))
                .isInstanceOfSatisfying(ReferenceNotFoundException.class,
                        notFound -> assertThat(notFound.getMessage()).contains("external_id " + EXTERNAL_ID));
    }

    @Test
    void activeByInsurer_isSoleActiveContractOfInsurerOnTodayInMoscow_withInsurer() {
        when(calendar.today()).thenReturn(TODAY);
        when(activeContracts.soleActiveOf(INSURER_EXTERNAL_ID, TODAY)).thenReturn(contract(INSURER_EXTERNAL_ID));
        when(insurers.findByExternalId(INSURER_EXTERNAL_ID))
                .thenReturn(Optional.of(ReadFixtures.insurer(INSURER_EXTERNAL_ID)));

        InsuranceContractResponse response = service.activeByInsurer(INSURER_EXTERNAL_ID);

        assertThat(response.externalId()).isEqualTo(EXTERNAL_ID);
        assertThat(response.insurer()).isNotNull()
                .extracting(InsuranceContractResponse.Organization::externalId).isEqualTo(INSURER_EXTERNAL_ID);
        verifyNoMoreInteractions(insuranceContracts);
    }

    @Test
    void activeByInsurer_insurerMissingInReference_isEmptyInsurer() {
        when(calendar.today()).thenReturn(TODAY);
        when(activeContracts.soleActiveOf(INSURER_EXTERNAL_ID, TODAY)).thenReturn(contract(INSURER_EXTERNAL_ID));
        when(insurers.findByExternalId(INSURER_EXTERNAL_ID)).thenReturn(Optional.empty());

        assertThat(service.activeByInsurer(INSURER_EXTERNAL_ID).insurer()).isNull();
    }

    private static InsuranceContract contract(@Nullable UUID externalInsurerId) {
        return ReadFixtures.contract(EXTERNAL_ID, externalInsurerId);
    }
}
