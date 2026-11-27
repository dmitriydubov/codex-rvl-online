package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import ru.projectx.contracts.entity.document.DocumentAttachments;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentBusyException;
import ru.projectx.contracts.exception.AttachmentLockedException;
import ru.projectx.contracts.exception.StorageUnavailableException;

class AttachmentDeletionTest {

    private static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    private static final StoredFile FILE = new StoredFile("private/object.pdf", "ab", 3, "application/pdf");
    private final PrintedForm active = PrintedForm.receivedAttachment(PrintedFormType.MEDICAL_CERTIFICATE,
            DocumentAttachments.certificate(UUID.randomUUID(), UUID.randomUUID(), ATTACHMENT), FILE, Instant.EPOCH);

    private final AttachmentDeletionTransactions transactions = mock();
    private final AttachmentObjectStore objects = mock();
    private final AttachmentMetrics metrics = mock();
    private final AttachmentDeletion deletion = new AttachmentDeletion(transactions, objects, metrics);

    @Test
    void missingOrAlreadyDeletedAttachmentDoesNotReachStorageOrCompletion() {
        deletion.delete(ATTACHMENT);

        verify(transactions).prepare(ATTACHMENT);
        verifyNoInteractions(objects, metrics);
        verify(transactions, never()).complete(ATTACHMENT, active);
    }

    @Test
    void successfulDeletionChecksRemovesAndCompletesInThatOrder() {
        when(transactions.prepare(ATTACHMENT)).thenReturn(Optional.of(active));

        deletion.delete(ATTACHMENT);

        var order = inOrder(transactions, objects);
        order.verify(transactions).prepare(ATTACHMENT);
        order.verify(objects).remove(FILE.key());
        order.verify(transactions).complete(ATTACHMENT, active);
        verifyNoInteractions(metrics);
    }

    @Test
    void storageFailureIsCountedWithoutCompletingDeletion() {
        when(transactions.prepare(ATTACHMENT)).thenReturn(Optional.of(active));
        var failure = new StorageUnavailableException();
        doThrow(failure).when(objects).remove(FILE.key());

        assertThatThrownBy(() -> deletion.delete(ATTACHMENT)).isSameAs(failure);
        verify(metrics).countStorageUnavailable();
        verify(transactions, never()).complete(ATTACHMENT, active);
    }

    @Test
    void firstLockTimeoutLeavesStorageAlone() {
        when(transactions.prepare(ATTACHMENT)).thenThrow(new CannotAcquireLockException("timeout"));

        assertThatThrownBy(() -> deletion.delete(ATTACHMENT)).isInstanceOf(AttachmentBusyException.class);
        verifyNoInteractions(objects, metrics);
    }

    @Test
    void secondLockTimeoutFollowsSuccessfulStorageRemoval() {
        when(transactions.prepare(ATTACHMENT)).thenReturn(Optional.of(active));
        doThrow(new CannotAcquireLockException("timeout")).when(transactions).complete(ATTACHMENT, active);

        assertThatThrownBy(() -> deletion.delete(ATTACHMENT)).isInstanceOf(AttachmentBusyException.class);
        verify(objects).remove(FILE.key());
        verifyNoInteractions(metrics);
    }

    @Test
    void submittedContractPreventsTheStorageCall() {
        when(transactions.prepare(ATTACHMENT)).thenThrow(new AttachmentLockedException());

        assertThatThrownBy(() -> deletion.delete(ATTACHMENT)).isInstanceOf(AttachmentLockedException.class);
        verifyNoInteractions(objects, metrics);
    }
}
