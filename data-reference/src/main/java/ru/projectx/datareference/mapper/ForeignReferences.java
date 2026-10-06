package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.repository.ForeignReferenceRepository;
import tools.jackson.databind.JsonNode;

/**
 * Память о записях 1С:УСК, которые разбор признал не нашими: санаторий без вида деятельности РВЛ, страхователь,
 * договор чужого контрагента, услуга такого договора.
 *
 * <p>В справочниках таких записей нет, поэтому без этой памяти сообщение, которое на них ссылается, выглядело бы
 * как сообщение, пришедшее раньше своего родителя, и ждало бы его до отказа по сроку. Запись, которая потом стала
 * нашей, находится в справочнике раньше, чем здесь, и отметка ей не мешает.
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

    /** Признана ли запись не нашей. */
    boolean isForeign(ParentKind kind, UUID externalId) {
        return repository.existsByExternalIdAndKind(externalId, kind);
    }

    /** Запоминает запись как не нашу и пропускает её сообщение с причиной; повтор ничего не меняет. */
    ImportResult skipAsForeign(ParentKind kind, UUID externalId, String reason) {
        remember(kind, externalId);
        return ImportResult.skipped(reason);
    }

    /**
     * Запоминает запись сообщения, GUID которой лежит в поле {@code id}, как не нашу, если сообщение пропущено.
     * Сообщение без читаемого GUID записи ничего не оставляет: ссылаться на такую запись нечем.
     *
     * @return тот же исход
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
