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
import ru.projectx.datareference.repository.PolicyContractViewRepository;

/**
 * Полис для сервиса Договоров: выборка идёт сразу в проекцию без ПДн, по {@code id} и по {@code external_id},
 * а ненайденный полис — {@code POLICY_NOT_FOUND}, как у R-05.
 */
@ExtendWith(MockitoExtension.class)
class PolicyContractViewServiceTest {

    private static final UUID POLICY_ID = UUID.fromString("c0de0000-0000-4000-8000-000000000001");
    private static final UUID POLICY_EXTERNAL_ID = UUID.fromString("c0de0000-0000-4000-8000-000000000002");
    private static final PolicyContractView VIEW = new PolicyContractView(POLICY_ID, POLICY_EXTERNAL_ID, "П-0001",
            LocalDate.of(2026, 1, 1), null, false);

    @Mock
    private PolicyContractViewRepository policies;

    private PolicyContractViewService service;

    @BeforeEach
    void setUp() {
        service = new PolicyContractViewService(policies);
    }

    @Test
    void byId_returnsProjectionFoundById() {
        when(policies.findById(POLICY_ID, PolicyContractView.class)).thenReturn(Optional.of(VIEW));

        assertThat(service.byId(POLICY_ID)).isSameAs(VIEW);
    }

    @Test
    void byId_unknown_isPolicyNotFoundNamingId() {
        when(policies.findById(POLICY_ID, PolicyContractView.class)).thenReturn(Optional.empty());

        ReferenceNotFoundException notFound =
                catchThrowableOfType(ReferenceNotFoundException.class, () -> service.byId(POLICY_ID));

        assertThat(notFound.code()).isEqualTo("POLICY_NOT_FOUND");
        assertThat(notFound).hasMessage("полис " + POLICY_ID + " не найден");
    }

    @Test
    void byExternalId_returnsProjectionFoundByExternalId() {
        when(policies.findByExternalId(POLICY_EXTERNAL_ID, PolicyContractView.class)).thenReturn(Optional.of(VIEW));

        assertThat(service.byExternalId(POLICY_EXTERNAL_ID)).isSameAs(VIEW);
    }

    @Test
    void byExternalId_unknown_isPolicyNotFoundNamingExternalId() {
        when(policies.findByExternalId(POLICY_EXTERNAL_ID, PolicyContractView.class)).thenReturn(Optional.empty());

        ReferenceNotFoundException notFound = catchThrowableOfType(ReferenceNotFoundException.class,
                () -> service.byExternalId(POLICY_EXTERNAL_ID));

        assertThat(notFound.code()).isEqualTo("POLICY_NOT_FOUND");
        assertThat(notFound).hasMessage("полис с external_id " + POLICY_EXTERNAL_ID + " не найден");
    }
}
