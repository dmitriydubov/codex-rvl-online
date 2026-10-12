package ru.projectx.datareference.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AmbiguousReferenceExceptionTest {

    private static final UUID FIRST = UUID.fromString("0a9e0000-0000-4000-8000-000000000001");
    private static final UUID SECOND = UUID.fromString("0a9e0000-0000-4000-8000-000000000002");
    private static final UUID LPU = UUID.fromString("0a9e0000-0000-4000-8000-000000000003");

    @Test
    void policy_listsPolicyCandidates() {
        AmbiguousReferenceException ambiguous = AmbiguousReferenceException.policy(List.of(FIRST, SECOND));

        assertThat(ambiguous.code()).isEqualTo("AMBIGUOUS_POLICY");
        assertThat(ambiguous).hasMessage("по номеру полиса в договоре найдено больше одной записи");
        assertThat(ambiguous.candidateKind()).isEqualTo(AmbiguousReferenceException.CandidateKind.POLICY);
        assertThat(ambiguous.candidateIds()).containsExactly(FIRST, SECOND);
    }

    @Test
    void priceContract_listsLpuContractCandidates_andNamesLpu() {
        AmbiguousReferenceException ambiguous = AmbiguousReferenceException.priceContract(LPU, List.of(SECOND, FIRST));

        assertThat(ambiguous.code()).isEqualTo(AmbiguousReferenceException.AMBIGUOUS_PRICE_CONTRACT);
        assertThat(ambiguous.code()).isEqualTo("AMBIGUOUS_PRICE_CONTRACT");
        assertThat(ambiguous)
                .hasMessage("у ЛПУ " + LPU + " больше одного действующего кандидата на прайсовый договор");
        assertThat(ambiguous.candidateKind()).isEqualTo(AmbiguousReferenceException.CandidateKind.LPU_CONTRACT);
        assertThat(ambiguous.candidateIds()).containsExactly(SECOND, FIRST);
    }

    @Test
    void candidates_doNotFollowLaterChangesOfCallersList() {
        List<UUID> found = new ArrayList<>(List.of(FIRST));
        AmbiguousReferenceException ambiguous = AmbiguousReferenceException.policy(found);

        found.add(SECOND);

        assertThat(ambiguous.candidateIds()).containsExactly(FIRST);
        assertThatThrownBy(() -> ambiguous.candidateIds().add(SECOND))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
