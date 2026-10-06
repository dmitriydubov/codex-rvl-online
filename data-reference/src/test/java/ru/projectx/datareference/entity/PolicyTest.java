package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Полис замещает обе табличные части целиком: строка, пропавшая из нового сообщения, пропадает и из полиса,
 * иначе у застрахованного копились бы программы и условия, которых у него уже нет. Каждая строка хранит
 * признак доступности как пришёл и получает собственный идентификатор.
 */
class PolicyTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-10-02T09:00:00Z");
    private static final LocalDate BEGIN = LocalDate.of(2026, 1, 1);
    private static final UUID FIRST_PROGRAM = UUID.fromString("6a1f0c3e-1b2d-4e7a-9c1b-000000000001");
    private static final UUID SECOND_PROGRAM = UUID.fromString("6a1f0c3e-1b2d-4e7a-9c1b-000000000002");
    private static final UUID THIRD_PROGRAM = UUID.fromString("6a1f0c3e-1b2d-4e7a-9c1b-000000000003");

    @Test
    void create_takesPolicyDetailsFromMessage() {
        Policy.Details details = details("П-0001", "Тестов", LocalDate.of(2026, 12, 31), false);

        Policy policy = Policy.create(UUID.randomUUID(), new Policy.Snapshot(details, List.of(), List.of()), CREATED);

        assertThat(policy.details()).isEqualTo(details);
        assertThat(policy.isArchive()).isFalse();
    }

    @Test
    void detailsText_leavesOutPersonalData_butNamesTheContract() {
        Policy.Details details = details("П-0001", "Тестов", LocalDate.of(2026, 12, 31), false);

        String text = details.toString();

        assertThat(text).contains(details.externalContractId().toString())
                .doesNotContain("Тестов", "Тестович", "1990-01-01", "test@example.org", "Мужской");
    }

    @Test
    void applySnapshot_replacesDetails_archivesByDeleteMark_andMarksUpdateTime() {
        Policy policy = Policy.create(UUID.randomUUID(), snapshot(List.of(), List.of()), CREATED);
        Policy.Details updated = details("П-0002", "Изменённая", null, true);

        policy.applySnapshot(new Policy.Snapshot(updated, List.of(), List.of()), UPDATED);

        assertThat(policy.details()).isEqualTo(updated);
        assertThat(policy.isArchive()).isTrue();
        assertThat(policy.updatedAt()).isEqualTo(UPDATED);
        assertThat(policy.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void applySnapshot_replacesProgramsInsteadOfAddingToOldOnes() {
        Policy policy = Policy.create(UUID.randomUUID(),
                snapshot(List.of(program(FIRST_PROGRAM, true), program(SECOND_PROGRAM, true)), List.of()), CREATED);

        policy.applySnapshot(snapshot(List.of(program(THIRD_PROGRAM, true)), List.of()), UPDATED);

        assertThat(policy.programs()).extracting(PolicyProgram::externalProgramId).containsExactly(THIRD_PROGRAM);
    }

    @Test
    void applySnapshot_replacesServiceLinesInsteadOfAddingToOldOnes() {
        Policy policy = Policy.create(UUID.randomUUID(),
                snapshot(List.of(), List.of(serviceLine(FIRST_PROGRAM, true), serviceLine(SECOND_PROGRAM, true))),
                CREATED);

        policy.applySnapshot(snapshot(List.of(), List.of(serviceLine(THIRD_PROGRAM, true))), UPDATED);

        assertThat(policy.serviceLines()).extracting(PolicyServiceLine::externalProgramId)
                .containsExactly(THIRD_PROGRAM);
    }

    @Test
    void programRows_keepAvailabilityAsReceived_andEachHasItsOwnId() {
        Policy policy = Policy.create(UUID.randomUUID(),
                snapshot(List.of(program(FIRST_PROGRAM, true), program(SECOND_PROGRAM, false)), List.of()), CREATED);

        List<PolicyProgram> programs = policy.programs();

        assertThat(programs).extracting(PolicyProgram::availability).containsExactly(true, false);
        assertThat(programs.get(0).id()).isNotNull().isNotEqualTo(programs.get(1).id());
    }

    @Test
    void serviceLineRows_keepAvailabilityAsReceived_andEachHasItsOwnId() {
        Policy policy = Policy.create(UUID.randomUUID(),
                snapshot(List.of(), List.of(serviceLine(FIRST_PROGRAM, true), serviceLine(SECOND_PROGRAM, false))),
                CREATED);

        List<PolicyServiceLine> lines = policy.serviceLines();

        assertThat(lines).extracting(PolicyServiceLine::availability).containsExactly(true, false);
        assertThat(lines.get(0).id()).isNotNull().isNotEqualTo(lines.get(1).id());
    }

    private static Policy.Snapshot snapshot(List<PolicyProgram.Row> programs, List<PolicyServiceLine.Row> lines) {
        var details = new Policy.Details(UUID.randomUUID(), "П-0001", "Тестов", "Тест", null,
                LocalDate.of(1990, 1, 1), null, null, BEGIN, null, true, false);
        return new Policy.Snapshot(details, programs, lines);
    }

    private static Policy.Details details(String number, String lastName, @Nullable LocalDate endDate,
            boolean deleteMark) {
        return new Policy.Details(UUID.randomUUID(), number, lastName, "Тест", "Тестович",
                LocalDate.of(1990, 1, 1), "Мужской", "test@example.org", BEGIN, endDate, !deleteMark, deleteMark);
    }

    private static PolicyProgram.Row program(UUID programId, boolean availability) {
        return new PolicyProgram.Row(programId, BEGIN, null, null, null, null, null, BigDecimal.TEN, availability);
    }

    private static PolicyServiceLine.Row serviceLine(UUID programId, boolean availability) {
        return new PolicyServiceLine.Row(programId, 1, BigDecimal.ONE, BEGIN, null, availability);
    }
}
