package ru.projectx.datareference.service.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.dto.InsuredRelativesResponse;
import ru.projectx.datareference.dto.InsuredRelativesResponse.Relative;
import ru.projectx.datareference.dto.RelativePolicy;
import ru.projectx.datareference.service.ReadFixtures;

class InsuredRelativesTest {

    private static final UUID CONTRACT = UUID.fromString("77ff17b5-e659-4720-b57f-e8a19c00fcea");
    private static final UUID FIRST = UUID.fromString("11111111-2222-4333-8444-555555555502");
    private static final UUID SECOND = UUID.fromString("11111111-2222-4333-8444-555555555503");
    private static final UUID SAME_NUMBER = UUID.fromString("f1111111-2222-4333-8444-555555555504");
    private static final UUID MISSING = UUID.fromString("11111111-2222-4333-8444-555555555509");
    private static final UUID LOW_TYPE = UUID.fromString("4b4e0f45-2c50-11e8-8123-005056b43f21");
    private static final UUID HIGH_TYPE = UUID.fromString("f97093be-2c51-11e8-8123-005056b43f21");

    @Test
    void relatives_carryTheTypeOfTheirLinkAndThePolicyOfTheRelative() {
        InsuredRelativesResponse response = InsuredRelatives.of(List.of(new RelationLink(FIRST, LOW_TYPE)),
                Map.of(FIRST, policy(FIRST, "СТЕНД-0002")));

        assertThat(response.items()).singleElement().satisfies(relative -> {
            assertThat(relative.relationTypeExternalId()).isEqualTo(LOW_TYPE);
            assertThat(relative.policy().externalId()).isEqualTo(FIRST);
        });
    }

    @Test
    void linkToPolicyNotInBase_isLeftOut() {
        InsuredRelativesResponse response = InsuredRelatives.of(
                List.of(new RelationLink(MISSING, LOW_TYPE), new RelationLink(FIRST, LOW_TYPE)),
                Map.of(FIRST, policy(FIRST, "СТЕНД-0002")));

        assertThat(response.items()).extracting(relative -> relative.policy().externalId()).containsExactly(FIRST);
    }

    @Test
    void relatives_goByPolicyNumber_thenByTypeGuidText_thenByPolicyGuidText() {
        InsuredRelativesResponse response = InsuredRelatives.of(List.of(
                        new RelationLink(SAME_NUMBER, LOW_TYPE),
                        new RelationLink(SECOND, LOW_TYPE),
                        new RelationLink(FIRST, HIGH_TYPE),
                        new RelationLink(FIRST, LOW_TYPE)),
                Map.of(FIRST, policy(FIRST, "СТЕНД-0002"), SECOND, policy(SECOND, "СТЕНД-0003"),
                        SAME_NUMBER, policy(SAME_NUMBER, "СТЕНД-0003")));

        assertThat(response.items())
                .extracting(relative -> relative.policy().externalId(), Relative::relationTypeExternalId)
                .containsExactly(
                        tuple(FIRST, LOW_TYPE),
                        tuple(FIRST, HIGH_TYPE),
                        tuple(SECOND, LOW_TYPE),
                        tuple(SAME_NUMBER, LOW_TYPE));
    }

    private static RelativePolicy policy(UUID externalId, String number) {
        return RelativePolicy.of(ReadFixtures.policy(externalId, number, CONTRACT, List.of(), List.of()), null, Map.of());
    }
}
