package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.FixedClockConfig.NOW;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import ru.projectx.datareference.entity.catalog.Mkb10;
import ru.projectx.datareference.repository.Mkb10Repository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class Mkb10ApiTest {

    private static final String BASE = InternalApi.BASE_PATH;
    private static final UUID Q06 = UUID.fromString("6b1d0000-0000-4000-8000-000000000001");
    private static final UUID Q06_0 = UUID.fromString("6b1d0000-0000-4000-8000-000000000002");
    private static final UUID I25_1 = UUID.fromString("6b1d0000-0000-4000-8000-000000000003");
    private static final UUID Z99 = UUID.fromString("6b1d0000-0000-4000-8000-000000000004");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private Mkb10Repository mkb10Repository;

    @BeforeEach
    void prepareCodes() {
        databaseCleaner.clear();
        save(Q06, null, "Q06", "Другие врожденные аномалии спинного мозга", 2, false);
        save(Q06_0, Q06, "Q06.0", "Амиелия", 3, false);
        save(I25_1, null, "I25.1", "Атеросклеротическая болезнь сердца", 3, false);
        save(Z99, null, "Z99", "Зависимость от аппаратов (архив)", 2, true);
    }

    @Test
    void query_startingWithCode_findsByPrefixWithParentCode() {
        MvcTestResult result = mvc.get().uri(BASE + "/mkb10?query={q}", "q0").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[*].code").asArray().containsExactly("Q06", "Q06.0");
        assertThat(result).bodyJson().extractingPath("$.items[0].parent_code").isNull();
        assertThat(result).bodyJson().extractingPath("$.items[1].parent_code").isEqualTo("Q06");
        assertThat(result).bodyJson().extractingPath("$.items[1].hierarchy_level").isEqualTo(3);
    }

    @Test
    void query_asText_findsByNameSubstring_withoutArchived() {
        MvcTestResult result = mvc.get().uri(BASE + "/mkb10?query={q}", "СПИНН").exchange();

        assertThat(result).bodyJson().extractingPath("$.items[*].code").asArray().containsExactly("Q06");

        MvcTestResult archived = mvc.get().uri(BASE + "/mkb10?query={q}", "аппарат").exchange();
        assertThat(archived).bodyJson().extractingPath("$.items").asArray().isEmpty();
    }

    @Test
    void codes_returnListedCodesIncludingArchived_sortedByCode() {
        MvcTestResult result = mvc.get().uri(BASE + "/mkb10?codes={codes}", "z99, i25.1,UNKNOWN").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[*].code").asArray().containsExactly("I25.1", "Z99");
        assertThat(result).bodyJson().extractingPath("$.items[1].is_archive").isEqualTo(true);
    }

    @Test
    void limitAndValidation() {
        MvcTestResult limited = mvc.get().uri(BASE + "/mkb10?query={q}&limit=1", "q0").exchange();
        assertThat(limited).bodyJson().extractingPath("$.items").asArray().hasSize(1);

        MvcTestResult shortQuery = mvc.get().uri(BASE + "/mkb10?query={q}", "q").exchange();
        assertThat(shortQuery).hasStatus(400);
        assertThat(shortQuery).bodyJson().extractingPath("$.error.details[0].field").isEqualTo("query");

        MvcTestResult noKey = mvc.get().uri(BASE + "/mkb10").exchange();
        assertThat(noKey).hasStatus(400);
        assertThat(noKey).bodyJson().extractingPath("$.error.code").isEqualTo("VALIDATION_ERROR");
    }

    private void save(UUID externalId, @Nullable UUID parent, String code, String name, int level, boolean archived) {
        mkb10Repository.save(Mkb10.create(externalId, new Mkb10.Details(parent, code, name, null, level, archived), NOW));
    }
}
