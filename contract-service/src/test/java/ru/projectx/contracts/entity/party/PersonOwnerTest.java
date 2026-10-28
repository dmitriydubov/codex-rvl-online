package ru.projectx.contracts.entity.party;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonOwnerTest {

    private static final UUID ID = UUID.fromString("3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4");

    @Test
    void contract_ownsPersonsThroughContractIdOnly() {
        assertThat(PersonOwner.contract(ID)).isEqualTo(new PersonOwner(ID, null));
    }

    @Test
    void lmPolicy_ownsPersonsThroughPolicyIdOnly() {
        assertThat(PersonOwner.lmPolicy(ID)).isEqualTo(new PersonOwner(null, ID));
    }
}
