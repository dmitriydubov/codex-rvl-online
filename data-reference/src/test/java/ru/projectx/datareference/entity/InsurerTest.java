package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Поля сообщения применяются к страхователю уже при создании, а не только при следующем обновлении.
 * Пометка удаления становится признаком архивности: удалённый в 1С страхователь остаётся в справочнике
 * архивной записью, а не пропадает. Следующее сообщение заменяет реквизиты целиком, включая пустые.
 */
class InsurerTest {

    private static final Instant CREATED = Instant.parse("2026-10-01T09:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-10-02T09:00:00Z");

    @Test
    void create_takesRequisitesFromMessage() {
        Insurer insurer = Insurer.create(UUID.randomUUID(),
                new Insurer.Details("ООО «Страхователь»", "Общество «Страхователь»", "7701000000", "770101001", false),
                CREATED);

        assertThat(insurer.name()).isEqualTo("ООО «Страхователь»");
        assertThat(insurer.fullName()).isEqualTo("Общество «Страхователь»");
        assertThat(insurer.inn()).isEqualTo("7701000000");
        assertThat(insurer.kpp()).isEqualTo("770101001");
    }

    @Test
    void create_withDeleteMark_isArchivedFromTheStart() {
        Insurer insurer = Insurer.create(UUID.randomUUID(),
                new Insurer.Details("ООО «Страхователь»", null, null, null, true), CREATED);

        assertThat(insurer.isArchive()).isTrue();
    }

    @Test
    void applySnapshot_withDeleteMark_archivesAndMarksUpdateTime() {
        Insurer insurer = Insurer.create(UUID.randomUUID(),
                new Insurer.Details("ООО «Страхователь»", null, null, null, false), CREATED);

        insurer.applySnapshot(new Insurer.Details("ООО «Новое имя»", null, null, null, true), UPDATED);

        assertThat(insurer.name()).isEqualTo("ООО «Новое имя»");
        assertThat(insurer.isArchive()).isTrue();
        assertThat(insurer.updatedAt()).isEqualTo(UPDATED);
        assertThat(insurer.createdAt()).isEqualTo(CREATED);
    }

    @Test
    void applySnapshot_replacesRequisitesEntirely_includingEmptyOnes() {
        Insurer insurer = Insurer.create(UUID.randomUUID(),
                new Insurer.Details("ООО «Страхователь»", "Общество «Страхователь»", "7701000000", "770101001", false),
                CREATED);

        insurer.applySnapshot(new Insurer.Details("ООО «Страхователь»", null, null, null, false), UPDATED);

        assertThat(insurer.fullName()).isNull();
        assertThat(insurer.inn()).isNull();
        assertThat(insurer.kpp()).isNull();
    }
}
