package ru.projectx.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.party.Gender;

class EnumsTest {

    @Test
    void named_findsConstantByExactName() {
        assertThat(Enums.named(Gender.class, "FEMALE")).contains(Gender.FEMALE);
    }

    @Test
    void named_isEmptyForUnknownOrDifferentlyCasedName() {
        assertThat(Enums.named(Gender.class, "female")).isEmpty();
        assertThat(Enums.named(Gender.class, "UNKNOWN")).isEmpty();
    }
}
