package ru.projectx.contracts.entity.party;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContractPersonTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final UUID CONTRACT = UUID.fromString("3f77b1c8-2a55-4c19-9e30-6b8d2f0a71c4");
    private static final UUID PARTICIPANT = UUID.fromString("9275471c-d2fe-484f-97a6-d6d7b1267b5e");

    @Test
    void snapshot_keepsEveryRequisiteOfThePerson() {
        PersonSnapshot person = new PersonSnapshot(PARTICIPANT, "Тестова", "Теста", "Тестовна",
                LocalDate.parse("1988-07-02"), Gender.FEMALE, "SPOUSE",
                new IdentityDocument(IdentityDocumentType.RF_PASSPORT, "4512", "654321", "Тестовый орган",
                        LocalDate.parse("2012-08-15"), "770-001"), "Тестовый адрес");

        ContractPerson stored = ContractPerson.snapshot(PersonOwner.contract(CONTRACT), PersonRole.INSURED, person, NOW);

        assertThat(stored.role()).isEqualTo(PersonRole.INSURED);
        assertThat(stored.person()).isEqualTo(person);
        assertThat(stored).hasFieldOrPropertyWithValue("contractId", CONTRACT)
                .hasFieldOrPropertyWithValue("lmPolicyId", null)
                .hasFieldOrPropertyWithValue("snapshotAt", NOW)
                .hasFieldOrPropertyWithValue("createdAt", NOW);
        assertThat(stored).extracting("id").isNull();
    }

    @Test
    void snapshot_withoutOptionalRequisites_keepsThemEmpty() {
        PersonSnapshot person = new PersonSnapshot(PARTICIPANT, "Тестов", "Тестик", null,
                LocalDate.parse("2017-05-30"), Gender.MALE, null,
                new IdentityDocument(IdentityDocumentType.BIRTH_CERTIFICATE, null, "512345", null, null, null), null);

        ContractPerson stored = ContractPerson.snapshot(PersonOwner.lmPolicy(CONTRACT), PersonRole.INSURED, person, NOW);

        assertThat(stored.person()).isEqualTo(person);
        assertThat(stored).hasFieldOrPropertyWithValue("contractId", null)
                .hasFieldOrPropertyWithValue("lmPolicyId", CONTRACT);
    }
}
