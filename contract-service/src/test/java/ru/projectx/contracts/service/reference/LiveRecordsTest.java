package ru.projectx.contracts.service.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.dto.ReferencePolicy;
import ru.projectx.contracts.exception.ReferenceArchivedException;
import ru.projectx.contracts.exception.ReferenceKind;

/**
 * Через порт проходят только живые записи: архивная запись останавливает выпуск по участнику ошибкой, которая
 * называет справочник и идентификатор записи.
 */
class LiveRecordsTest {

    private static final UUID ID = UUID.fromString("4c6cb8a5-2d7e-4b65-9a1f-0f9d9d2a7c11");
    private static final UUID EXTERNAL_ID = UUID.fromString("9e6ee30d-2275-417d-a44c-039d3b8c2ac5");

    @Test
    void liveRecord_passesThroughUnchanged() {
        ReferencePolicy live = policy(false);

        assertThat(LiveRecords.requireLive(ReferenceKind.POLICY, live)).isSameAs(live);
    }

    @Test
    void archivedRecord_isRejectedWithReferenceAndId() {
        assertThatThrownBy(() -> LiveRecords.requireLive(ReferenceKind.POLICY, policy(true)))
                .isInstanceOfSatisfying(ReferenceArchivedException.class, archived -> {
                    assertThat(archived.kind()).isEqualTo(ReferenceKind.POLICY);
                    assertThat(archived.id()).isEqualTo(ID);
                });
    }

    private static ReferencePolicy policy(boolean archived) {
        return new ReferencePolicy(ID, EXTERNAL_ID, "СТЕНД-0001", LocalDate.of(2026, 1, 1), null, archived);
    }
}
