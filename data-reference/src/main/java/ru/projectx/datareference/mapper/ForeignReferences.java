package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.repository.ForeignReferenceRepository;
import tools.jackson.databind.JsonNode;

@Component
class ForeignReferences {

    private static final String RECORD_ID = "id";

    private final ForeignReferenceRepository repository;
    private final Clock clock;

    ForeignReferences(ForeignReferenceRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    boolean isForeign(ParentKind kind, UUID externalId) {
        return repository.existsByExternalIdAndKind(externalId, kind);
    }

    ImportResult skipAsForeign(ParentKind kind, UUID externalId, String reason) {
        remember(kind, externalId);
        return ImportResult.skipped(reason);
    }

    ImportResult rememberIfSkipped(ImportResult result, ParentKind kind, JsonNode data) {
        if (result instanceof ImportResult.Skipped) {
            OneCValues.uuid(data, RECORD_ID).ifPresent(externalId -> remember(kind, externalId));
        }
        return result;
    }

    private void remember(ParentKind kind, UUID externalId) {
        repository.remember(externalId, kind.name(), clock.instant());
    }
}
