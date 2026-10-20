package ru.projectx.datareference.service.lpu;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.projectx.datareference.support.FixedClockConfig.NOW;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.entity.catalog.AppealType;
import ru.projectx.datareference.repository.catalog.AppealTypeRepository;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class RvlAppealTypesTest {

    @Autowired
    private RvlAppealTypes rvlAppealTypes;

    @Autowired
    private AppealTypeRepository appealTypeRepository;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void emptyReference_reportsEveryConfiguredNameMissing() {
        assertThat(rvlAppealTypes.missing()).containsExactly("РВЛ и СКЛ", "РВЛ в России");
        assertThat(rvlAppealTypes.externalIds()).isEmpty();
    }

    @Test
    void namesMatchIgnoringCaseAndEdgeSpaces_archivedDoNotCount() {
        UUID skl = UUID.randomUUID();
        save(skl, "  рвл и скл ", false);
        save(UUID.randomUUID(), "РВЛ в России", true);

        assertThat(rvlAppealTypes.externalIds()).containsExactly(skl);
        assertThat(rvlAppealTypes.missing()).containsExactly("РВЛ в России");
    }

    private void save(UUID externalId, String name, boolean archived) {
        appealTypeRepository.save(AppealType.create(externalId,
                new AppealType.Details(null, null, name, null, null, null, true, false, archived), NOW));
    }
}
