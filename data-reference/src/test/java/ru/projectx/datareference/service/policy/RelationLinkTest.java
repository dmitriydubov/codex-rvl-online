package ru.projectx.datareference.service.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.policy.InsuredAssociation;

class RelationLinkTest {

    private static final UUID INSURED = UUID.fromString("11111111-2222-4333-8444-555555555501");
    private static final UUID SPOUSE = UUID.fromString("11111111-2222-4333-8444-555555555502");
    private static final UUID CHILD = UUID.fromString("11111111-2222-4333-8444-555555555503");
    private static final UUID TYPE = UUID.fromString("797093be-2c51-11e8-8123-005056b43f21");
    private static final UUID OTHER_TYPE = UUID.fromString("4b4e0f45-2c50-11e8-8123-005056b43f21");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    @Test
    void activeOn_keepsOnlyRowsValidThatDay() {
        List<InsuredAssociation.Row> rows = List.of(
                row(SPOUSE, TYPE, LocalDate.of(2026, 1, 1), null),
                row(CHILD, TYPE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 14)));

        assertThat(RelationLink.activeOn(rows, TODAY)).containsExactly(new RelationLink(SPOUSE, TYPE));
    }

    @Test
    void activeOn_givesOneLinkPerRelatedPolicyAndType_howeverManyRowsCoverTheDay() {
        List<InsuredAssociation.Row> rows = List.of(
                row(SPOUSE, TYPE, LocalDate.of(2026, 1, 1), null),
                row(SPOUSE, TYPE, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30)),
                row(SPOUSE, OTHER_TYPE, LocalDate.of(2026, 1, 1), null));

        assertThat(RelationLink.activeOn(rows, TODAY))
                .containsExactly(new RelationLink(SPOUSE, TYPE), new RelationLink(SPOUSE, OTHER_TYPE));
    }

    private static InsuredAssociation.Row row(UUID related, UUID type, LocalDate from, LocalDate to) {
        return new InsuredAssociation.Row(INSURED, related, type, from, to);
    }
}
