package ru.projectx.datareference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.dto.PolicyContractView;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.PolicyContractRow;
import ru.projectx.datareference.repository.PolicyContractViewRepository;

@ExtendWith(MockitoExtension.class)
class PolicyContractViewServiceTest {

    private static final UUID POLICY_ID = UUID.fromString("c0de0000-0000-4000-8000-000000000001");
    private static final UUID POLICY_EXTERNAL_ID = UUID.fromString("c0de0000-0000-4000-8000-000000000002");
    private static final String NUMBER = "П-0001";
    private static final LocalDate BEGIN = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2030, 12, 31);
    private static final PolicyContractRow ARCHIVED_ROW =
            new PolicyContractRow(POLICY_ID, POLICY_EXTERNAL_ID, NUMBER, BEGIN, END, true);
    private static final PolicyContractView ARCHIVED_VIEW =
            new PolicyContractView(POLICY_ID, POLICY_EXTERNAL_ID, NUMBER, BEGIN, END, true);

    @Mock
    private PolicyContractViewRepository policies;

    private PolicyContractViewService service;

    @BeforeEach
    void setUp() {
        service = new PolicyContractViewService(policies);
    }

    @Test
    void byId_returnsRowFoundById_fieldForField() {
        when(policies.findContractViewById(POLICY_ID)).thenReturn(Optional.of(ARCHIVED_ROW));

        assertThat(service.byId(POLICY_ID)).isEqualTo(ARCHIVED_VIEW);
    }

    @Test
    void byId_keepsOpenEndAndActivePolicy() {
        var activeOpenEnded = new PolicyContractRow(POLICY_ID, POLICY_EXTERNAL_ID, NUMBER, BEGIN, null, false);
        when(policies.findContractViewById(POLICY_ID)).thenReturn(Optional.of(activeOpenEnded));

        assertThat(service.byId(POLICY_ID))
                .isEqualTo(new PolicyContractView(POLICY_ID, POLICY_EXTERNAL_ID, NUMBER, BEGIN, null, false));
    }

    @Test
    void byId_unknown_isPolicyNotFoundNamingId() {
        when(policies.findContractViewById(POLICY_ID)).thenReturn(Optional.empty());

        ReferenceNotFoundException notFound =
                catchThrowableOfType(ReferenceNotFoundException.class, () -> service.byId(POLICY_ID));

        assertThat(notFound.code()).isEqualTo("POLICY_NOT_FOUND");
        assertThat(notFound).hasMessage("полис " + POLICY_ID + " не найден");
    }

    @Test
    void byExternalId_returnsRowFoundByExternalId_fieldForField() {
        when(policies.findContractViewByExternalId(POLICY_EXTERNAL_ID)).thenReturn(Optional.of(ARCHIVED_ROW));

        assertThat(service.byExternalId(POLICY_EXTERNAL_ID)).isEqualTo(ARCHIVED_VIEW);
    }

    @Test
    void byExternalId_unknown_isPolicyNotFoundNamingExternalId() {
        when(policies.findContractViewByExternalId(POLICY_EXTERNAL_ID)).thenReturn(Optional.empty());

        ReferenceNotFoundException notFound = catchThrowableOfType(ReferenceNotFoundException.class,
                () -> service.byExternalId(POLICY_EXTERNAL_ID));

        assertThat(notFound.code()).isEqualTo("POLICY_NOT_FOUND");
        assertThat(notFound).hasMessage("полис с external_id " + POLICY_EXTERNAL_ID + " не найден");
    }
}
