package ru.projectx.datareference.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Страхователь заполняется из сообщения уже при создании, а каждое следующее сообщение переносит пометку
 * удаления в признак архивности и сдвигает время обновления: по этим полям сервис Заявок видит, жив ли
 * страхователь и когда его последний раз подтвердили.
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
}
