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
import ru.projectx.datareference.entity.Mkb10;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.Mkb10Repository;
import ru.projectx.datareference.service.onec.ImportResult;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает разбор справочника МКБ-10. Справочник приходит целиком и без фильтра: каждое сообщение
 * создаёт диагноз или заменяет его реквизиты последним полученным снимком. Код диагноза приходит
 * дополненным пробелами и хранится без них, иначе поиск по коду его не найдёт. Без GUID, кода или
 * названия сообщение нарушает контракт, и в справочник ничего не пишется.
 */
class Mkb10MapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID DIAGNOSIS = UUID.fromString("f7dc59ec-b18e-11ec-9e23-0894ef5d42f9");
    private static final UUID PARENT = UUID.fromString("c7997f5a-b18e-11ec-9e23-0894ef5d42f9");
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Instant EARLIER = NOW.minusSeconds(3600);

    private final Mkb10Repository repository = mock(Mkb10Repository.class);
    private final Mkb10Mapper mapper = new Mkb10Mapper(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void streamIsMkb10() {
        assertThat(mapper.stream()).isEqualTo(OneCStream.MKB10);
    }

    @Test
    void newDiagnosis_isCreatedFromTheMessage() {
        when(repository.findByExternalId(DIAGNOSIS)).thenReturn(Optional.empty());

        ImportResult result = mapper.apply(diagnosis());

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        Mkb10 saved = saved();
        assertThat(saved.externalId()).isEqualTo(DIAGNOSIS);
        assertThat(saved.externalParentId()).isEqualTo(PARENT);
        assertThat(saved.code()).isEqualTo("Q06");
        assertThat(saved.name()).isEqualTo("Пороки развития спинного мозга");
        assertThat(saved.systemCode()).isEqualTo("062.001.006");
        assertThat(saved.hierarchyLevel()).isEqualTo(3);
        assertThat(saved.isArchive()).isFalse();
        assertThat(saved.createdAt()).isEqualTo(NOW);
    }

    @Test
    void rootDiagnosis_withEmptyOptionalFields_isStoredWithoutThem() {
        when(repository.findByExternalId(DIAGNOSIS)).thenReturn(Optional.empty());
        ObjectNode data = diagnosis();
        data.put("parentId", "00000000-0000-0000-0000-000000000000");
        data.remove("systemCode");
        data.remove("hierarchyLevels");

        mapper.apply(data);

        Mkb10 saved = saved();
        assertThat(saved.externalParentId()).isNull();
        assertThat(saved.systemCode()).isNull();
        assertThat(saved.hierarchyLevel()).isNull();
    }

    @Test
    void knownDiagnosis_takesTheLastSnapshot() {
        Mkb10 known = knownDiagnosis();
        when(repository.findByExternalId(DIAGNOSIS)).thenReturn(Optional.of(known));

        ImportResult result = mapper.apply(diagnosis());

        assertThat(result).isInstanceOf(ImportResult.Applied.class);
        assertThat(saved()).isSameAs(known);
        assertThat(known.code()).isEqualTo("Q06");
        assertThat(known.name()).isEqualTo("Пороки развития спинного мозга");
        assertThat(known.createdAt()).isEqualTo(EARLIER);
        assertThat(known.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void deleteMark_archivesTheDiagnosis() {
        Mkb10 known = knownDiagnosis();
        when(repository.findByExternalId(DIAGNOSIS)).thenReturn(Optional.of(known));
        ObjectNode data = diagnosis();
        data.put("deleteMark", true);

        mapper.apply(data);

        assertThat(known.isArchive()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "code", "name"})
    void messageWithoutRequiredField_isContractViolation_andNothingIsSaved(String field) {
        ObjectNode data = diagnosis();
        data.remove(field);

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageEndingWith(" " + field);
        verify(repository, never()).save(any());
    }

    @Test
    void messageWithoutSeveralRequiredFields_namesTheGuidFirst() {
        ObjectNode data = diagnosis();
        data.remove("name");
        data.remove("code");
        data.remove("id");

        assertThatThrownBy(() -> mapper.apply(data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageEndingWith(" id");
        verify(repository, never()).save(any());
    }

    private Mkb10 saved() {
        ArgumentCaptor<Mkb10> saved = ArgumentCaptor.forClass(Mkb10.class);
        verify(repository).save(saved.capture());
        return saved.getValue();
    }

    private static Mkb10 knownDiagnosis() {
        var details = new Mkb10.Details(null, "Q05", "Старое название", null, null, false);
        return Mkb10.create(DIAGNOSIS, details, EARLIER);
    }

    private static ObjectNode diagnosis() {
        ObjectNode data = JSON.createObjectNode();
        data.put("id", DIAGNOSIS.toString());
        data.put("parentId", PARENT.toString());
        data.put("code", "Q06  ");
        data.put("name", "Пороки развития спинного мозга");
        data.put("systemCode", "062.001.006");
        data.put("hierarchyLevels", 3);
        data.put("deleteMark", false);
        return data;
    }
}
