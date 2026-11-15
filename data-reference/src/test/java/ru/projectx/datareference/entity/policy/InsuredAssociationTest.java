package ru.projectx.datareference.entity.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InsuredAssociationTest {

    private static final UUID INSURED = UUID.fromString("11111111-2222-4333-8444-555555555501");
    private static final UUID RELATED = UUID.fromString("11111111-2222-4333-8444-555555555502");
    private static final UUID TYPE = UUID.fromString("797093be-2c51-11e8-8123-005056b43f21");

    @Test
    void association_keepsEveryValueOfItsRow() {
        InsuredAssociation.Row row = new InsuredAssociation.Row(INSURED, RELATED, TYPE, LocalDate.of(2026, 11, 13),
                LocalDate.of(2027, 3, 31));

        assertThat(InsuredAssociation.of(row, Instant.parse("2026-11-15T10:00:00Z")).row()).isEqualTo(row);
    }

    @Test
    void row_namesThePolicyOfTheInsuredFirst() {
        InsuredAssociation.Row row = new InsuredAssociation.Row(INSURED, RELATED, TYPE, LocalDate.of(2026, 11, 13),
                null);

        assertThat(row.policies()).containsExactly(INSURED, RELATED);
    }
}
