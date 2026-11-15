package ru.projectx.datareference.service.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.dto.InsuredRelativesResponse;
import ru.projectx.datareference.dto.PolicyResponse;
import ru.projectx.datareference.entity.policy.InsuredAssociation;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.exception.ReferenceNotFoundException;
import ru.projectx.datareference.repository.policy.InsuredAssociationRepository;
import ru.projectx.datareference.repository.policy.PolicyRepository;
import ru.projectx.datareference.service.ReadFixtures;
import ru.projectx.datareference.service.rest.ReferenceCalendar;
import ru.projectx.datareference.support.TestApiProperties;

@ExtendWith(MockitoExtension.class)
class InsuredRelativesServiceTest {

    private static final UUID CONTRACT = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID INSURED = UUID.fromString("c0000000-0000-4000-8000-000000000002");
    private static final UUID SPOUSE = UUID.fromString("c0000000-0000-4000-8000-000000000003");
    private static final UUID FORMER = UUID.fromString("c0000000-0000-4000-8000-000000000004");
    private static final UUID TYPE = UUID.fromString("797093be-2c51-11e8-8123-005056b43f21");
    private static final String NUMBER = "СТЕНД-0001";

    @Mock
    private PolicyRepository policies;

    @Mock
    private InsuredAssociationRepository associations;

    @Mock
    private RelativePolicies relativePolicies;

    private InsuredRelativesService service;

    @BeforeEach
    void setUp() {
        Clock moscowMidnight = Clock.fixed(Instant.parse("2026-11-14T21:00:01Z"), ZoneOffset.UTC);
        service = new InsuredRelativesService(new PolicyLookup(policies), associations, relativePolicies,
                new ReferenceCalendar(moscowMidnight, TestApiProperties.defaults()));
    }

    @Test
    void ofPolicy_givesRelativesLinkedToTheInsuredPolicy_onTheMoscowDay() {
        Policy insured = ReadFixtures.policy(INSURED, NUMBER, CONTRACT, List.of(), List.of());
        when(policies.findByExternalId(INSURED)).thenReturn(Optional.of(insured));
        linksOfInsured(row(SPOUSE, LocalDate.of(2026, 11, 15)), row(FORMER, LocalDate.of(2026, 11, 16)));
        when(relativePolicies.byExternalIds(List.of(SPOUSE))).thenReturn(Map.of(SPOUSE,
                PolicyResponse.of(
                        ReadFixtures.policy(SPOUSE, "СТЕНД-0002", CONTRACT, List.of(), List.of()), null, Map.of())));

        InsuredRelativesResponse response = service.ofPolicy(INSURED);

        assertThat(response.items()).singleElement().satisfies(relative -> {
            assertThat(relative.policy().externalId()).isEqualTo(SPOUSE);
            assertThat(relative.relationTypeExternalId()).isEqualTo(TYPE);
        });
    }

    @Test
    void ofPolicyNumber_findsTheInsuredByNumberInContract() {
        Policy insured = ReadFixtures.policy(INSURED, NUMBER, CONTRACT, List.of(), List.of());
        when(policies.findAllByNumberAndExternalContractId(NUMBER, CONTRACT)).thenReturn(List.of(insured));
        linksOfInsured();
        when(relativePolicies.byExternalIds(List.of())).thenReturn(Map.of());

        assertThat(service.ofPolicyNumber(NUMBER, CONTRACT).items()).isEmpty();
    }

    @Test
    void unknownInsuredPolicy_isPolicyNotFound_withoutReadingLinks() {
        when(policies.findByExternalId(INSURED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ofPolicy(INSURED)).isInstanceOf(ReferenceNotFoundException.class);
        verifyNoInteractions(associations, relativePolicies);
    }

    private void linksOfInsured(InsuredAssociation.Row... rows) {
        when(associations.findAllByInsuredPolicyExternalId(INSURED)).thenReturn(List.of(rows).stream()
                .map(row -> InsuredAssociation.of(row, ReadFixtures.NOW))
                .toList());
    }

    private static InsuredAssociation.Row row(UUID related, LocalDate validFrom) {
        return new InsuredAssociation.Row(INSURED, related, TYPE, validFrom, null);
    }
}
