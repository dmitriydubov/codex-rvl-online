package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProgramTest {

    private static final UUID EXTERNAL_ID = UUID.fromString("2d3e4f5a-6b7c-4d8e-9fa0-000000000001");
    private static final UUID PRODUCT_ID = UUID.fromString("2d3e4f5a-6b7c-4d8e-9fa0-000000000002");
    private static final UUID PARENT_ID = UUID.fromString("2d3e4f5a-6b7c-4d8e-9fa0-000000000003");
    private static final Instant CREATED = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-10-02T09:00:00Z");

    @Test
    void create_takesHeaderFieldsFromMessage_andStartsWithEmptyLpuList() {
        Program program = Program.create(EXTERNAL_ID,
                new Program.Details(PRODUCT_ID, PARENT_ID, "Программа РВЛ", "Основная", false), CREATED);

        assertThat(program.externalId()).isEqualTo(EXTERNAL_ID);
        assertThat(program.externalProductId()).isEqualTo(PRODUCT_ID);
        assertThat(program.externalParentId()).isEqualTo(PARENT_ID);
        assertThat(program.name()).isEqualTo("Программа РВЛ");
        assertThat(program.typeProgramm()).isEqualTo("Основная");
        assertThat(program.isArchive()).isFalse();
        assertThat(program.availableLpu()).isEmpty();
    }

    @Test
    void create_withDeleteMark_isArchivedFromTheStart() {
        Program program = Program.create(EXTERNAL_ID, details("Программа РВЛ", true), CREATED);

        assertThat(program.isArchive()).isTrue();
    }

    @Test
    void applySnapshot_replacesHeaderFields_archivesByDeleteMark_andMarksUpdateTime() {
        Program program = Program.create(EXTERNAL_ID,
                new Program.Details(PRODUCT_ID, PARENT_ID, "Программа РВЛ", "Основная", false), CREATED);

        program.applySnapshot(details("Новое имя", true), UPDATED);

        assertThat(program.externalProductId()).isNull();
        assertThat(program.externalParentId()).isNull();
        assertThat(program.name()).isEqualTo("Новое имя");
        assertThat(program.typeProgramm()).isNull();
        assertThat(program.isArchive()).isTrue();
        assertThat(program.updatedAt()).isEqualTo(UPDATED);
        assertThat(program.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void archiveAfterLosingRvl_appliesFields_andArchivesWithoutDeleteMark() {
        Program program = Program.create(EXTERNAL_ID, details("Программа РВЛ", false), CREATED);

        program.archiveAfterLosingRvl(details("Уже не РВЛ", false), UPDATED);

        assertThat(program.name()).isEqualTo("Уже не РВЛ");
        assertThat(program.isArchive()).isTrue();
        assertThat(program.updatedAt()).isEqualTo(UPDATED);
    }

    @Test
    void replaceAvailableLpu_replacesListInsteadOfAddingToOldOne() {
        Program program = Program.create(EXTERNAL_ID, details("Программа РВЛ", false), CREATED);
        Lpu first = lpu("Первый санаторий");
        Lpu second = lpu("Второй санаторий");
        Lpu third = lpu("Третий санаторий");
        program.replaceAvailableLpu(List.of(row(first), row(second)), CREATED);

        program.replaceAvailableLpu(List.of(row(third)), UPDATED);

        assertThat(program.availableLpu()).extracting(ProgramLpu::lpu).containsExactly(third);
    }

    @Test
    void replaceAvailableLpu_keepsEveryRowOfMessage_inMessageOrder() {
        Program program = Program.create(EXTERNAL_ID, details("Программа РВЛ", false), CREATED);
        Lpu first = lpu("Первый санаторий");
        Lpu second = lpu("Второй санаторий");

        program.replaceAvailableLpu(List.of(row(first), row(second)), CREATED);

        assertThat(program.availableLpu()).extracting(ProgramLpu::lpu).containsExactly(first, second);
    }

    @Test
    void availableLpu_cannotBeChangedFromOutside() {
        Program program = Program.create(EXTERNAL_ID, details("Программа РВЛ", false), CREATED);
        program.replaceAvailableLpu(List.of(row(lpu("Санаторий"))), CREATED);
        List<ProgramLpu> lpuList = program.availableLpu();

        assertThatThrownBy(lpuList::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    private static Program.Details details(String name, boolean deleteMark) {
        return new Program.Details(null, null, name, null, deleteMark);
    }

    private static Lpu lpu(String name) {
        return Lpu.create(UUID.randomUUID(), new Lpu.Details(name, null, null, null, null, null, false), CREATED);
    }

    private static ProgramLpu.Row row(Lpu lpu) {
        return new ProgramLpu.Row(lpu, null, null, null, 1, false, BigDecimal.ZERO, ProgramLpu.CoverageType.R);
    }
}
