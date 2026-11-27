package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private final AttachmentObjectReads objects = mock();
    private final AttachmentMetrics metrics = mock();
    private final AttachmentDownloads downloads = new AttachmentDownloads(registry, objects, metrics);

    @Test
    void opensTheStoredKeyAndTransfersOwnershipOfTheStream() throws Exception {
        activeAttachment();
        var stream = new ByteArrayInputStream(new byte[] {1, 0, 3});
        when(objects.open(FILE.key())).thenReturn(stream);

        var content = downloads.open(ATTACHMENT);
        assertThat(content.contentType()).isEqualTo("application/pdf");
        assertThat(content.size()).isEqualTo(3);
        assertThat(content.hash()).isEqualTo("ab");
        try (var opened = content.stream()) {
            assertThat(opened.read()).isEqualTo(1);
            byte[] remaining = new byte[2];
            assertThat(opened.read(remaining)).isEqualTo(2);
            assertThat(remaining).containsExactly(0, 3);
            assertThat(opened.read(remaining)).isEqualTo(-1);
        }
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
                    .hasNoCause().hasMessageNotContaining(FILE.key());
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

    @Test
    void failedBodyReadCountsTheOutageAndSanitizesTheFailure() throws Exception {
        activeAttachment();
        var source = mock(InputStream.class);
        when(source.read()).thenThrow(new IOException(FILE.key()));
        when(objects.open(FILE.key())).thenReturn(source);

        try (var stream = downloads.open(ATTACHMENT).stream()) {
            assertThatThrownBy(stream::read).isInstanceOf(StorageUnavailableException.class)
                    .hasNoCause().hasMessageNotContaining(FILE.key());
        }
        verify(source).close();
        verify(metrics).countStorageUnavailable();
    }

    @Test
    void failedCloseDoesNotExposeTheRawStorageError() throws Exception {
        activeAttachment();
        var source = mock(InputStream.class);
        doThrow(new IOException(FILE.key())).when(source).close();
        when(objects.open(FILE.key())).thenReturn(source);

        var stream = downloads.open(ATTACHMENT).stream();
        assertThatThrownBy(stream::close).isInstanceOf(IOException.class).hasNoCause()
                .hasMessageNotContaining(FILE.key());
    }

    private void activeAttachment() {
        var owner = DocumentAttachments.consent(UUID.randomUUID(), UUID.randomUUID(), ATTACHMENT);
        when(registry.active(ATTACHMENT)).thenReturn(Optional.of(
                PrintedForm.receivedAttachment(PrintedFormType.PD_CONSENT, owner, FILE, Instant.EPOCH)));
    }
}
