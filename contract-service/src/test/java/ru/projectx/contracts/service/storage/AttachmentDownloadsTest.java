package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ru.projectx.contracts.entity.document.DocumentAttachments;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentNotFoundException;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;

class AttachmentDownloadsTest {

    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final StoredFile FILE = new StoredFile("private/object.pdf", "ab", 3, "application/pdf");

    private final AttachmentRegistry registry = mock();
    private final AttachmentObjectStore objects = mock();
    private final AttachmentMetrics metrics = mock();
    private final AttachmentDownloads downloads = new AttachmentDownloads(registry, objects, metrics);

    @Test
    void opensTheStoredKeyAndTransfersOwnershipOfTheStream() {
        activeAttachment();
        var stream = new ByteArrayInputStream(new byte[] {1, 0, 3});
        when(objects.open(FILE.key())).thenReturn(stream);

        assertThat(downloads.open(ATTACHMENT)).isEqualTo(new AttachmentContent(stream, "application/pdf", 3, "ab"));
        verifyNoInteractions(metrics);
    }

    @Test
    void missingRegistryRowDoesNotReachStorage() {
        assertThatThrownBy(() -> downloads.open(ATTACHMENT)).isInstanceOf(AttachmentNotFoundException.class);
        verifyNoInteractions(objects, metrics);
    }

    @Test
    void missingObjectLogsOnlyTheAttachmentIdentifierAndDoesNotCountAnOutage() {
        activeAttachment();
        var missing = new CertificateMissingException(new IllegalStateException(FILE.key()));
        when(objects.open(FILE.key())).thenThrow(missing);
        var journal = new ListAppender<ILoggingEvent>();
        Logger logger = (Logger) LoggerFactory.getLogger(AttachmentDownloads.class);
        journal.start();
        logger.addAppender(journal);
        try {
            assertThatThrownBy(() -> downloads.open(ATTACHMENT)).isInstanceOf(AttachmentNotFoundException.class)
                    .hasNoCause().satisfies(failure ->
                            assertThat(failure.getStackTrace()).isEqualTo(missing.getStackTrace()));
            assertThat(journal.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).isEqualTo("в хранилище отсутствует объект вложения " + ATTACHMENT);
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(journal);
            journal.stop();
        }
        verifyNoInteractions(metrics);
    }

    @Test
    void storageFailureCountsTheExistingMetricAndPropagatesTheDomainFailure() {
        activeAttachment();
        var unavailable = new StorageUnavailableException();
        when(objects.open(FILE.key())).thenThrow(unavailable);

        assertThatThrownBy(() -> downloads.open(ATTACHMENT)).isSameAs(unavailable);
        verify(metrics).countStorageUnavailable();
    }

    private void activeAttachment() {
        var owner = DocumentAttachments.consent(UUID.randomUUID(), UUID.randomUUID(), ATTACHMENT);
        when(registry.active(ATTACHMENT)).thenReturn(Optional.of(
                PrintedForm.receivedAttachment(PrintedFormType.PD_CONSENT, owner, FILE, Instant.EPOCH)));
    }
}
