package ru.projectx.contracts.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ru.projectx.contracts.entity.MessageAttachment;
import ru.projectx.contracts.entity.StoredFile;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;
import ru.projectx.contracts.repository.MessageAttachmentRepository;
import ru.projectx.contracts.service.storage.AttachmentMetrics;
import ru.projectx.contracts.service.storage.CertificateStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OutboundBodiesTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:32:00Z");
    private static final UUID MESSAGE_ID = UUID.fromString("0196c0f1-7a2b-7c3d-8e4f-5a6b7c8d9e0f");
    private static final UUID FIRST = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final UUID SECOND = UUID.fromString("1a4c7e2f-9b30-4d85-a6f1-c2e8b05d7f39");
    private static final String FIRST_KEY = "certificates/0b6c/3c2f.jpg";
    private static final String SECOND_KEY = "certificates/0b6c/1a4c.pdf";
    private static final byte[] FIRST_CONTENT = "СТЕНД: вымышленная справка 070/у, участник 1"
            .getBytes(StandardCharsets.UTF_8);
    private static final String PAYLOAD = "{\"meta\":{\"entity_version\":3},\"data\":{\"premium_amount\":34000.00,"
            + "\"documents\":[{\"document_id\":\"" + FIRST + "\",\"doc_type\":\"MEDICAL_CERTIFICATE\",\"file_hash\":\"a\"},"
            + "{\"document_id\":\"" + SECOND + "\",\"doc_type\":\"MEDICAL_CERTIFICATE\",\"file_hash\":\"b\"}]}}";

    private final MessageAttachmentRepository attachments = mock();
    private final CertificateStore store = mock();
    private final AttachmentMetrics metrics = mock();
    private final JsonMapper json = JsonMapper.builder().build();
    private final OutboundBodies bodies = new OutboundBodies(attachments, store, metrics, json);
    private final ListAppender<ILoggingEvent> journal = new ListAppender<>();

    @BeforeEach
    void twoAttachmentsAreStoredForTheMessage() {
        given(attachments.findByOutboxMessageIdOrderByCreatedAtAscIdAsc(MESSAGE_ID)).willReturn(List.of(
                attachment(SECOND, SECOND_KEY, "application/pdf"), attachment(FIRST, FIRST_KEY, "image/jpeg")));
        journal.start();
        logger().addAppender(journal);
    }

    @AfterEach
    void stopJournal() {
        logger().detachAppender(journal);
        journal.stop();
    }

    @Test
    void messageWithoutAttachments_isSentAsStoredWithoutTouchingTheStorage() {
        given(attachments.findByOutboxMessageIdOrderByCreatedAtAscIdAsc(MESSAGE_ID)).willReturn(List.of());

        assertThat(bodies.complete(MESSAGE_ID, PAYLOAD)).isEqualTo(new OutboundBody.Complete(PAYLOAD));
        verifyNoInteractions(store, metrics);
    }

    @Test
    void attachments_areReadFromTheStorageAndPutInBase64IntoTheirDocumentsByDocumentId() {
        given(store.read(FIRST_KEY)).willReturn(FIRST_CONTENT);
        given(store.read(SECOND_KEY)).willReturn(new byte[] {1, 2, 3, 4});

        OutboundBody body = bodies.complete(MESSAGE_ID, PAYLOAD);

        assertThat(body).isInstanceOf(OutboundBody.Complete.class);
        JsonNode sent = json.readTree(((OutboundBody.Complete) body).payload());
        JsonNode documents = sent.path("data").path("documents");
        assertThat(documents.size()).isEqualTo(2);
        assertThat(documents.path(0).path("document_id").stringValue()).isEqualTo(FIRST.toString());
        assertThat(documents.path(0).path("content_base64").stringValue()).isEqualTo(
                "0KHQotCV0J3QlDog0LLRi9C80YvRiNC70LXQvdC90LDRjyDRgdC/0YDQsNCy0LrQsCAwNzAv0YMsINGD0YfQsNGB0YLQvdC40LogMQ==");
        assertThat(documents.path(0).path("file_hash").stringValue()).isEqualTo("a");
        assertThat(documents.path(1).path("document_id").stringValue()).isEqualTo(SECOND.toString());
        assertThat(documents.path(1).path("content_base64").stringValue()).isEqualTo("AQIDBA==");
        assertThat(sent.path("meta").path("entity_version").intValue()).isEqualTo(3);
        verifyNoInteractions(metrics);
    }

    @Test
    void completedBody_keepsTheAmountWithItsTwoDecimalsAndAppendsTheContentAfterTheStoredFields() {
        given(store.read(FIRST_KEY)).willReturn(FIRST_CONTENT);
        given(store.read(SECOND_KEY)).willReturn(new byte[] {1, 2, 3, 4});

        String sent = ((OutboundBody.Complete) bodies.complete(MESSAGE_ID, PAYLOAD)).payload();

        assertThat(sent).contains("\"premium_amount\":34000.00,")
                .contains("\"file_hash\":\"a\",\"content_base64\":\"")
                .contains("\"file_hash\":\"b\",\"content_base64\":\"AQIDBA==\"}");
    }

    @Test
    void unavailableStorage_withholdsTheMessageUntilTheNextPassWithACountAndAWarningWithoutTheKey() {
        given(store.read(SECOND_KEY)).willThrow(new StorageUnavailableException(new IOException("Connection refused")));

        OutboundBody body = bodies.complete(MESSAGE_ID, PAYLOAD);

        assertThat(body).isEqualTo(new OutboundBody.Withheld(new PublishOutcome.NotSent("хранилище справок недоступно")));
        verify(metrics).countStorageUnavailable();
        assertThat(journal.list).filteredOn(event -> event.getLevel() == Level.WARN)
                .extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly("вложения сообщения " + MESSAGE_ID + " не прочитаны: хранилище справок недоступно: "
                        + "IOException");
    }

    @Test
    void missingObject_refusesTheMessageForGoodWithoutCountingTheStorageAsUnavailable() {
        given(store.read(SECOND_KEY)).willThrow(new CertificateMissingException(new IOException("NoSuchKey")));

        OutboundBody body = bodies.complete(MESSAGE_ID, PAYLOAD);

        assertThat(body).isEqualTo(new OutboundBody.Withheld(new PublishOutcome.Refused(
                "объекта справки нет в хранилище")));
        verifyNoInteractions(metrics);
    }

    @Test
    void attachmentWithoutItsDocumentInTheBody_isIllegalState() {
        UUID stranger = UUID.fromString("00000000-0000-4000-8000-000000000001");
        given(attachments.findByOutboxMessageIdOrderByCreatedAtAscIdAsc(MESSAGE_ID)).willReturn(List.of(
                attachment(stranger, "certificates/0b6c/0000.jpg", "image/jpeg")));

        assertThatThrownBy(() -> bodies.complete(MESSAGE_ID, PAYLOAD)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(stranger.toString());
        verifyNoInteractions(store);
    }

    private static MessageAttachment attachment(UUID documentId, String key, String contentType) {
        return MessageAttachment.of(MESSAGE_ID, documentId, new StoredFile(key, "0".repeat(64), 76L, contentType), NOW);
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(OutboundBodies.class);
    }
}
