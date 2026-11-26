package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.unit.DataSize;
import ru.projectx.contracts.config.storage.AttachmentProperties;
import ru.projectx.contracts.dto.AttachmentDetails;
import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.exception.AttachmentRejectedException;
import ru.projectx.contracts.exception.RequestCancelledException;
import ru.projectx.contracts.exception.StorageUnavailableException;

class AttachmentReceptionTest {

    private static final String KEY = "certificates/0b6c1d2e-7f3a-4b5c-9d8e-1f2a3b4c5d6e/" + Uploads.ATTACHMENT + ".jpg";
    private static final AttachmentResponse RESPONSE = new AttachmentResponse(Uploads.REQUEST, Uploads.PARTICIPANT,
            new AttachmentDetails(UUID.randomUUID(), Uploads.ATTACHMENT, "image/jpeg", 76, Uploads.CONTENT_HASH,
                    Instant.parse("2026-02-10T07:05:00Z")));

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AttachmentMetrics metrics = new AttachmentMetrics(meters);
    private final AttachmentRegistry registry = mock(AttachmentRegistry.class);
    private final ObjectKeys keys = mock(ObjectKeys.class);
    private final CertificateStore store = mock(CertificateStore.class);
    private final AttachmentReception reception = new AttachmentReception(
            new AttachmentFiles(new AttachmentProperties(DataSize.ofMegabytes(5), Map.of("image/jpeg", "jpg")), metrics),
            registry, keys, store, metrics);

    @Test
    void newCertificate_isStoredUnderANewKeyThenRegisteredAndCounted() {
        AttachmentUpload upload = Uploads.jpeg();
        when(registry.accepted(upload, Uploads.CONTENT_HASH)).thenReturn(Optional.empty());
        when(keys.next(Uploads.ATTACHMENT, "jpg")).thenReturn(KEY);
        when(registry.register(eq(upload), any(StoredFile.class))).thenReturn(RESPONSE);

        assertThat(reception.receive(upload)).isSameAs(RESPONSE);

        StoredFile expected = new StoredFile(KEY, Uploads.CONTENT_HASH, 76, "image/jpeg");
        InOrder order = inOrder(store, registry);
        order.verify(store).store(eq(expected), any(ContentSource.class));
        order.verify(registry).register(upload, expected);
        assertThat(meters.get("contracts.attachments.stored").counter().count()).isEqualTo(1);
    }

    @Test
    void storedObject_receivesTheUploadedContent() throws IOException {
        AttachmentUpload upload = Uploads.jpeg();
        when(registry.accepted(upload, Uploads.CONTENT_HASH)).thenReturn(Optional.empty());
        when(keys.next(Uploads.ATTACHMENT, "jpg")).thenReturn(KEY);

        reception.receive(upload);

        ArgumentCaptor<ContentSource> content = ArgumentCaptor.forClass(ContentSource.class);
        verify(store).store(any(StoredFile.class), content.capture());
        assertThat(content.getValue().open().readAllBytes()).isEqualTo(upload.file().source().open().readAllBytes());
    }

    @Test
    void repeatedAttachment_returnsThePriorRecordWithoutStoringOrCounting() {
        AttachmentUpload upload = Uploads.jpeg();
        when(registry.accepted(upload, Uploads.CONTENT_HASH)).thenReturn(Optional.of(RESPONSE));

        assertThat(reception.receive(upload)).isSameAs(RESPONSE);

        verifyNoInteractions(store, keys);
        verify(registry, never()).register(any(), any());
        assertThat(meters.find("contracts.attachments.stored").counter()).isNull();
    }

    @Test
    void rejectedFile_checksRequestButDoesNotLookUpAnAttachmentOrStoreIt() {
        AttachmentUpload upload = Uploads.of(Uploads.PARTICIPANT, "image/png", Uploads.CONTENT);

        assertThatThrownBy(() -> reception.receive(upload)).isInstanceOf(AttachmentRejectedException.class);

        verify(registry).ensureOpenFor(upload);
        verify(registry, never()).accepted(any(), any());
        verifyNoInteractions(store, keys);
    }

    @Test
    void cancelledRequest_takesPriorityOverFileRejection() {
        AttachmentUpload upload = Uploads.of(Uploads.PARTICIPANT, "image/gif", Uploads.CONTENT);
        RequestCancelledException cancelled = new RequestCancelledException(Uploads.REQUEST);
        doThrow(cancelled).when(registry).ensureOpenFor(upload);

        assertThatThrownBy(() -> reception.receive(upload)).isSameAs(cancelled);
        assertThat(meters.find("contracts.attachments.rejected").counter()).isNull();
        verifyNoInteractions(store, keys);
    }

    @Test
    void conflictingAttachment_isNotStored() {
        AttachmentUpload upload = Uploads.jpeg();
        when(registry.accepted(upload, Uploads.CONTENT_HASH)).thenThrow(new AttachmentConflictException(Uploads.ATTACHMENT));

        assertThatThrownBy(() -> reception.receive(upload)).isInstanceOf(AttachmentConflictException.class);

        verifyNoInteractions(store);
    }

    @Test
    void unavailableStorage_isCountedAndRethrownWithoutRegistering() {
        AttachmentUpload upload = Uploads.jpeg();
        when(registry.accepted(upload, Uploads.CONTENT_HASH)).thenReturn(Optional.empty());
        when(keys.next(Uploads.ATTACHMENT, "jpg")).thenReturn(KEY);
        StorageUnavailableException unavailable = new StorageUnavailableException(new IOException("refused"));
        doThrow(unavailable).when(store).store(any(), any());

        assertThatThrownBy(() -> reception.receive(upload)).isSameAs(unavailable);

        verify(registry, never()).register(any(), any());
        assertThat(meters.get("contracts.storage.unavailable").counter().count()).isEqualTo(1);
    }

    @Test
    void concurrentUploadOfTheSameAttachment_returnsTheWinningRecordWithoutCounting() {
        AttachmentUpload upload = Uploads.jpeg();
        when(registry.accepted(upload, Uploads.CONTENT_HASH)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(RESPONSE));
        when(keys.next(Uploads.ATTACHMENT, "jpg")).thenReturn(KEY);
        when(registry.register(eq(upload), any())).thenThrow(new DataIntegrityViolationException("uq"));

        assertThat(reception.receive(upload)).isSameAs(RESPONSE);

        assertThat(meters.find("contracts.attachments.stored").counter()).isNull();
    }

    @Test
    void integrityViolationWithoutAWinningRecord_isRethrown() {
        AttachmentUpload upload = Uploads.jpeg();
        when(registry.accepted(upload, Uploads.CONTENT_HASH)).thenReturn(Optional.empty());
        when(keys.next(Uploads.ATTACHMENT, "jpg")).thenReturn(KEY);
        DataIntegrityViolationException violation = new DataIntegrityViolationException("fk");
        when(registry.register(eq(upload), any())).thenThrow(violation);

        assertThatThrownBy(() -> reception.receive(upload)).isSameAs(violation);
    }
}
