package ru.projectx.datareference.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import ru.projectx.datareference.entity.AppealType;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.AppealTypeRepository;
import ru.projectx.datareference.service.onec.ImportResult;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает разбор справочника видов обращения. Справочник приходит целиком и без фильтра: каждое
 * сообщение создаёт вид обращения или заменяет его реквизиты последним полученным снимком. Без GUID
 * записи и без названия сообщение нарушает контракт, и в справочник ничего не пишется.
 */
class AppealTypeMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID APPEAL_TYPE = UUID.fromString("a861f691-e5d6-11e1-ba57-002481aec094");
    private static final UUID PARENT = UUID.fromString("40389a5d-5aa1-11db-a75b-000802ce8208");
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);

    private final AppealTypeRepository repository = mock(AppealTypeRepository.class);
    private final AppealTypeMapper mapper = new AppealTypeMapper(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void streamIsAppealTypes() {
        assertThat(mapper.stream()).isEqualTo(OneCStream.APPEAL_TYPES);
    }

    @Test
    void newAppealType_isCreatedFromTheMessage() {
        when(repository.findByExternalId(APPEAL_TYPE)).thenReturn(Optional.empty());

        ImportResult result = mapper.apply(appealType());

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        AppealType saved = saved();
        assertThat(saved.externalId()).isEqualTo(APPEAL_TYPE);
        assertThat(saved.externalParentId()).isEqualTo(PARENT);
        assertThat(saved.externalCode()).isEqualTo("000024");
        assertThat(saved.name()).isEqualTo("Восстановительное лечение");
        assertThat(saved.category()).isEqualTo("Санаторий");
        assertThat(saved.systemCode()).isEqualTo("003.022");
        assertThat(saved.hierarchyLevel()).isEqualTo(2);
        assertThat(saved.directAttachment()).isTrue();
        assertThat(saved.nonDirectAttachment()).isFalse();
        assertThat(saved.isArchive()).isFalse();
        assertThat(saved.createdAt()).isEqualTo(NOW);
    }

    @Test
    void indirectAttachment_isStoredAsNonDirectAttachment() {
        when(repository.findByExternalId(APPEAL_TYPE)).thenReturn(Optional.empty());
        ObjectNode data = appealType();
        data.put("directAttachment", false);
        data.put("indirectAttachment", true);

        mapper.apply(data);

        assertThat(saved().directAttachment()).isFalse();
        assertThat(saved().nonDirectAttachment()).isTrue();
    }

    @Test
    void emptyOptionalFields_areStoredAsEmpty() {
        when(repository.findByExternalId(APPEAL_TYPE)).thenReturn(Optional.empty());
        ObjectNode data = appealType();
        data.put("idParent", "00000000-0000-0000-0000-000000000000");
        data.put("code", "   ");
        data.put("category", "");
        data.remove("systemCode");
        data.remove("hierarchyLevel");

        mapper.apply(data);

        AppealType saved = saved();
        assertThat(saved.externalParentId()).isNull();
        assertThat(saved.externalCode()).isNull();
        assertThat(saved.category()).isNull();
        assertThat(saved.systemCode()).isNull();
        assertThat(saved.hierarchyLevel()).isNull();
    }

    @Test
    void knownAppealType_takesTheLastSnapshot() {
        AppealType known = AppealType.create(APPEAL_TYPE, oldDetails(), EARLIER);
        when(repository.findByExternalId(APPEAL_TYPE)).thenReturn(Optional.of(known));

        ImportResult result = mapper.apply(appealType());

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(saved()).isSameAs(known);
        assertThat(known.name()).isEqualTo("Восстановительное лечение");
        assertThat(known.externalParentId()).isEqualTo(PARENT);
        assertThat(known.directAttachment()).isTrue();
        assertThat(known.createdAt()).isEqualTo(EARLIER);
        assertThat(known.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void deleteMark_archivesTheAppealType() {
        AppealType known = AppealType.create(APPEAL_TYPE, oldDetails(), EARLIER);
        when(repository.findByExternalId(APPEAL_TYPE)).thenReturn(Optional.of(known));
        ObjectNode data = appealType();
        data.put("deleteMark", true);

        mapper.apply(data);

        assertThat(known.isArchive()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "name"})
    void messageWithoutRequiredField_isContractViolation_andNothingIsSaved(String field) {
        ObjectNode data = appealType();
        data.remove(field);

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageEndingWith(" " + field);
        verify(repository, never()).save(any());
    }

    private AppealType saved() {
        ArgumentCaptor<AppealType> saved = ArgumentCaptor.forClass(AppealType.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    private static AppealType.Details oldDetails() {
        return new AppealType.Details(null, "000001", "Старое название", null, null, 1, false, false, false);
    }

    private static ObjectNode appealType() {
        ObjectNode data = JSON.createObjectNode();
        data.put("id", APPEAL_TYPE.toString());
        data.put("idParent", PARENT.toString());
        data.put("code", "000024 ");
        data.put("name", "Восстановительное лечение");
        data.put("category", "Санаторий");
        data.put("systemCode", "003.022");
        data.put("hierarchyLevel", 2);
        data.put("directAttachment", true);
        data.put("indirectAttachment", false);
        data.put("deleteMark", false);
        return data;
    }
}
