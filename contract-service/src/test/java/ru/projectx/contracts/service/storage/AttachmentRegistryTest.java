package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import ru.projectx.contracts.dto.AttachmentDetails;
import ru.projectx.contracts.dto.AttachmentReceipt;
import ru.projectx.contracts.dto.ConsentResponse;
import ru.projectx.contracts.entity.document.AttachmentOwners;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.exception.RequestCancelledException;
import ru.projectx.contracts.repository.issuance.IssuanceRequestRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;

class AttachmentRegistryTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:05:00Z");
    private static final StoredFile FILE = new StoredFile("certificates/f/" + Uploads.ATTACHMENT + ".jpg",
            Uploads.CONTENT_HASH, 76, "image/jpeg");

    private final IssuanceRequestRepository requests = mock(IssuanceRequestRepository.class);
    private final PrintedFormRepository documents = mock(PrintedFormRepository.class);
    private final AttachmentRegistry registry = new AttachmentRegistry(requests, documents,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void unknownRequest_canReceiveCertificateBeforeIssuance() {
        when(requests.existsByIndividualRequestIdAndCancelledAtIsNotNull(Uploads.REQUEST)).thenReturn(false);

        assertThat(registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH)).isEmpty();
        verify(documents).findByAttachmentAttachmentId(Uploads.ATTACHMENT);
    }

    @Test
    void cancelledRequest_isRequestCancelledEvenWithoutParticipants() {
        when(requests.existsByIndividualRequestIdAndCancelledAtIsNotNull(Uploads.REQUEST)).thenReturn(true);

        assertThatThrownBy(() -> registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH))
                .isInstanceOf(RequestCancelledException.class).hasMessageContaining(Uploads.REQUEST.toString());
    }

    @Test
    void newAttachmentOfAParticipant_hasNoAcceptedRecord() {
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.empty());

        assertThat(registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH)).isEmpty();
    }

    @Test
    void sameAttachmentWithSameContent_isThePriorRecord() {
        PrintedForm prior = certificate(Uploads.PARTICIPANT, Uploads.CONTENT_HASH);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.of(prior));

        assertThat(registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH)).get()
                .extracting(AttachmentReceipt::details)
                .returns(Uploads.ATTACHMENT, AttachmentDetails::attachmentId)
                .returns(NOW.minusSeconds(300), AttachmentDetails::createdAt);
    }

    @Test
    void sameAttachmentWithOtherContent_isConflict() {
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT))
                .thenReturn(Optional.of(certificate(Uploads.PARTICIPANT, "0".repeat(64))));

        assertThatThrownBy(() -> registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH))
                .isInstanceOf(AttachmentConflictException.class).hasMessageContaining(Uploads.ATTACHMENT.toString());
    }

    @Test
    void sameAttachmentOfAnotherParticipant_isConflict() {
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT))
                .thenReturn(Optional.of(certificate(Uploads.OTHER_PARTICIPANT, Uploads.CONTENT_HASH)));

        assertThatThrownBy(() -> registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH))
                .isInstanceOf(AttachmentConflictException.class);
    }

    @Test
    void otherKindWithSameOwnerIdsAndContent_isConflict() {
        PrintedForm prior = PrintedForm.receivedAttachment(
                AttachmentOwners.consent(Uploads.REQUEST, Uploads.PARTICIPANT, Uploads.ATTACHMENT), FILE, NOW);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.of(prior));

        assertThatThrownBy(() -> registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH))
                .isInstanceOf(AttachmentConflictException.class);
    }

    @Test
    void deletedAttachmentWithSameOwnerAndContent_isConflict() {
        PrintedForm prior = certificate(Uploads.PARTICIPANT, Uploads.CONTENT_HASH);
        ReflectionTestUtils.setField(prior, "deletedAt", NOW);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.of(prior));

        assertThatThrownBy(() -> registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH))
                .isInstanceOf(AttachmentConflictException.class);
    }

    @Test
    void consentWithPolicyIdMatchingCancelledRequest_isRegisteredWithItsOwnOwner() {
        AttachmentUpload upload = AttachmentUpload.consent(Uploads.REQUEST, Uploads.PARTICIPANT,
                Uploads.ATTACHMENT, Uploads.jpeg().file());
        when(documents.saveAndFlush(any(PrintedForm.class))).thenAnswer(saved -> saved.getArgument(0));

        AttachmentDetails details = new AttachmentDetails(null, Uploads.ATTACHMENT,
                FILE.contentType(), FILE.size(), FILE.hash(), NOW);
        assertThat(registry.register(upload, FILE))
                .isEqualTo(new ConsentResponse(Uploads.REQUEST, Uploads.PARTICIPANT, details));
        verify(requests, never()).existsByIndividualRequestIdAndCancelledAtIsNotNull(any());
    }

    @Test
    void register_savesTheCertificateOfTheParticipantAtTheCurrentInstant() {
        when(documents.saveAndFlush(any(PrintedForm.class))).thenAnswer(saved -> saved.getArgument(0));

        AttachmentReceipt response = registry.register(Uploads.jpeg(), FILE);

        ArgumentCaptor<PrintedForm> saved = ArgumentCaptor.forClass(PrintedForm.class);
        verify(documents).saveAndFlush(saved.capture());
        assertThat(saved.getValue().attachment())
                .contains(AttachmentOwners.certificate(Uploads.REQUEST, Uploads.PARTICIPANT, Uploads.ATTACHMENT));
        assertThat(saved.getValue().storedFile()).contains(FILE);
        assertThat(saved.getValue().createdAt()).isEqualTo(NOW);
        assertThat(response.details().fileHash()).isEqualTo(Uploads.CONTENT_HASH);
        assertThat(response.details().createdAt()).isEqualTo(NOW);
    }

    @Test
    void requestCancelledWhileTheFileWasStored_isNotRegistered() {
        when(requests.existsByIndividualRequestIdAndCancelledAtIsNotNull(Uploads.REQUEST)).thenReturn(true);

        assertThatThrownBy(() -> registry.register(Uploads.jpeg(), FILE)).isInstanceOf(RequestCancelledException.class);

        verify(documents, never()).saveAndFlush(any());
    }

    private static PrintedForm certificate(UUID participant, String hash) {
        return PrintedForm.receivedAttachment(AttachmentOwners.certificate(Uploads.REQUEST, participant, Uploads.ATTACHMENT),
                new StoredFile(FILE.key(), hash, FILE.size(), FILE.contentType()), NOW.minusSeconds(300));
    }
}
