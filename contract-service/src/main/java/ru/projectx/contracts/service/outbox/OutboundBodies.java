package ru.projectx.contracts.service.outbox;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.MessageAttachment;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;
import ru.projectx.contracts.message.CertificateDocument;
import ru.projectx.contracts.repository.MessageAttachmentRepository;
import ru.projectx.contracts.service.storage.AttachmentMetrics;
import ru.projectx.contracts.service.storage.CertificateStore;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class OutboundBodies {

    static final String STORAGE_UNAVAILABLE = "хранилище справок недоступно";

    private static final Logger log = LoggerFactory.getLogger(OutboundBodies.class);
    private static final String DATA = "data";
    private static final String DOCUMENTS = "documents";
    private static final String DOCUMENT_ID = "document_id";

    private final MessageAttachmentRepository attachments;
    private final CertificateStore store;
    private final AttachmentMetrics metrics;
    private final ObjectReader reader;
    private final ObjectWriter writer;

    public OutboundBodies(MessageAttachmentRepository attachments, CertificateStore store, AttachmentMetrics metrics,
            JsonMapper json) {
        this.attachments = attachments;
        this.store = store;
        this.metrics = metrics;
        this.reader = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        this.writer = json.writer();
    }

    public OutboundBody complete(UUID outboxMessageId, String payload) {
        List<MessageAttachment> files = attachments.findByOutboxMessageIdOrderByCreatedAtAscIdAsc(outboxMessageId);
        if (files.isEmpty()) {
            return new OutboundBody.Complete(payload);
        }
        return withContents(outboxMessageId, payload, files);
    }

    private OutboundBody withContents(UUID outboxMessageId, String payload, List<MessageAttachment> files) {
        try {
            return new OutboundBody.Complete(filled(payload, files));
        } catch (StorageUnavailableException unavailable) {
            log.warn("вложения сообщения {} не прочитаны: {}", outboxMessageId, unavailable.getMessage());
            metrics.countStorageUnavailable();
            return new OutboundBody.Withheld(new PublishOutcome.NotSent(STORAGE_UNAVAILABLE));
        } catch (CertificateMissingException missing) {
            return new OutboundBody.Withheld(new PublishOutcome.Refused(missing.getMessage()));
        }
    }

    private String filled(String payload, List<MessageAttachment> files) {
        ObjectNode body = (ObjectNode) reader.readTree(payload);
        Map<UUID, ObjectNode> documents = documentsOf(body);
        for (MessageAttachment file : files) {
            ObjectNode document = Optional.ofNullable(documents.get(file.documentId()))
                    .orElseThrow(() -> new IllegalStateException(
                            "в теле сообщения нет документа " + file.documentId() + " из вложений"));
            document.put(CertificateDocument.CONTENT_FIELD, Base64.getEncoder().encodeToString(store.read(file.fileKey())));
        }
        return writer.writeValueAsString(body);
    }

    private static Map<UUID, ObjectNode> documentsOf(ObjectNode body) {
        Map<UUID, ObjectNode> byId = new HashMap<>();
        for (JsonNode document : body.path(DATA).path(DOCUMENTS)) {
            byId.put(UUID.fromString(document.path(DOCUMENT_ID).stringValue()), (ObjectNode) document);
        }
        return byId;
    }
}
