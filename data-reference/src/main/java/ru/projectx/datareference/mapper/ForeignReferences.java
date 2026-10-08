package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.repository.ForeignReferenceRepository;
import tools.jackson.databind.JsonNode;

/**
 * Память о записях 1С:УСК, признанных не нашими. Без неё сообщение, ссылающееся на такую запись, ждало бы её как
 * не пришедшего родителя до отказа по сроку. Запись, ставшая потом нашей, находится в справочнике раньше, чем здесь.
 */
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

    /** Запоминает запись как не нашу и пропускает её сообщение с причиной; повтор ничего не меняет. */
    ImportResult skipAsForeign(ParentKind kind, UUID externalId, String reason) {
        remember(kind, externalId);
        return ImportResult.skipped(reason);
    }

    /**
     * Запоминает запись пропущенного сообщения как не нашу и возвращает тот же исход. Сообщение без читаемого GUID
     * ничего не оставляет: ссылаться на такую запись нечем.
     */
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
