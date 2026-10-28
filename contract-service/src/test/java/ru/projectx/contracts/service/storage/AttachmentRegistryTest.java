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
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.projectx.contracts.dto.AttachmentResponse;
import ru.projectx.contracts.entity.issuance.DocumentKind;
import ru.projectx.contracts.entity.issuance.IssuanceRequest;
import ru.projectx.contracts.entity.document.ParticipantAttachment;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.issuance.RequestParticipant;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.exception.AttachmentConflictException;
import ru.projectx.contracts.exception.ParticipantNotFoundException;
import ru.projectx.contracts.exception.RequestCancelledException;
import ru.projectx.contracts.exception.RequestNotFoundException;
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
    void unknownRequest_isRequestNotFound() {
        when(requests.findById(Uploads.REQUEST)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH))
                .isInstanceOf(RequestNotFoundException.class).hasMessageContaining(Uploads.REQUEST.toString());
    }

    @Test
    void participantOutsideTheComposition_isParticipantNotFound() {
        knownRequest(false, Uploads.OTHER_PARTICIPANT);

        assertThatThrownBy(() -> registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH))
                .isInstanceOf(ParticipantNotFoundException.class).hasMessageContaining(Uploads.PARTICIPANT.toString());
    }

    @Test
    void cancelledRequest_isRequestCancelledEvenForItsParticipant() {
        knownRequest(true, Uploads.PARTICIPANT);

        assertThatThrownBy(() -> registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH))
                .isInstanceOf(RequestCancelledException.class).hasMessageContaining(Uploads.REQUEST.toString());
    }

    @Test
    void newAttachmentOfAParticipant_hasNoAcceptedRecord() {
        knownRequest(false, Uploads.OTHER_PARTICIPANT, Uploads.PARTICIPANT);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.empty());

        assertThat(registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH)).isEmpty();
    }

    @Test
    void sameAttachmentWithSameContent_isThePriorRecord() {
        knownRequest(false, Uploads.PARTICIPANT);
        PrintedForm prior = certificate(Uploads.PARTICIPANT, Uploads.CONTENT_HASH);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT)).thenReturn(Optional.of(prior));

        assertThat(registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH)).get()
                .returns(Uploads.ATTACHMENT, AttachmentResponse::attachmentId)
                .returns(NOW.minusSeconds(300), AttachmentResponse::createdAt);
    }

    @Test
    void sameAttachmentWithOtherContent_isConflict() {
        knownRequest(false, Uploads.PARTICIPANT);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT))
                .thenReturn(Optional.of(certificate(Uploads.PARTICIPANT, "0".repeat(64))));

        assertThatThrownBy(() -> registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH))
                .isInstanceOf(AttachmentConflictException.class).hasMessageContaining(Uploads.ATTACHMENT.toString());
    }

    @Test
    void sameAttachmentOfAnotherParticipant_isConflict() {
        knownRequest(false, Uploads.PARTICIPANT, Uploads.OTHER_PARTICIPANT);
        when(documents.findByAttachmentAttachmentId(Uploads.ATTACHMENT))
                .thenReturn(Optional.of(certificate(Uploads.OTHER_PARTICIPANT, Uploads.CONTENT_HASH)));

        assertThatThrownBy(() -> registry.accepted(Uploads.jpeg(), Uploads.CONTENT_HASH))
                .isInstanceOf(AttachmentConflictException.class);
    }

    @Test
    void register_savesTheCertificateOfTheParticipantAtTheCurrentInstant() {
        knownRequest(false, Uploads.PARTICIPANT);
        when(documents.saveAndFlush(any(PrintedForm.class))).thenAnswer(saved -> saved.getArgument(0));

        AttachmentResponse response = registry.register(Uploads.jpeg(), FILE);

        ArgumentCaptor<PrintedForm> saved = ArgumentCaptor.forClass(PrintedForm.class);
        verify(documents).saveAndFlush(saved.capture());
        assertThat(saved.getValue().attachment())
                .contains(new ParticipantAttachment(Uploads.REQUEST, Uploads.PARTICIPANT, Uploads.ATTACHMENT));
        assertThat(saved.getValue().storedFile()).contains(FILE);
        assertThat(saved.getValue().createdAt()).isEqualTo(NOW);
        assertThat(response.fileHash()).isEqualTo(Uploads.CONTENT_HASH);
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void requestCancelledWhileTheFileWasStored_isNotRegistered() {
        knownRequest(true, Uploads.PARTICIPANT);

        assertThatThrownBy(() -> registry.register(Uploads.jpeg(), FILE)).isInstanceOf(RequestCancelledException.class);

        verify(documents, never()).saveAndFlush(any());
    }

    @Test
    void participantExcludedWhileTheFileWasStored_isNotRegistered() {
        knownRequest(false, Uploads.OTHER_PARTICIPANT);

        assertThatThrownBy(() -> registry.register(Uploads.jpeg(), FILE))
                .isInstanceOf(ParticipantNotFoundException.class);

        verify(documents, never()).saveAndFlush(any());
    }

    private void knownRequest(boolean cancelled, UUID... participants) {
        IssuanceRequest request = IssuanceRequest.start(Uploads.REQUEST, NOW);
        request.takeComposition(Arrays.stream(participants)
                .map(participant -> new RequestParticipant(participant, DocumentKind.LM_POLICY)).toList(), NOW);
        if (cancelled) {
            request.cancel(NOW);
        }
        when(requests.findById(Uploads.REQUEST)).thenReturn(Optional.of(request));
    }

    private static PrintedForm certificate(UUID participant, String hash) {
        return PrintedForm.receivedCertificate(new ParticipantAttachment(Uploads.REQUEST, participant, Uploads.ATTACHMENT),
                new StoredFile(FILE.key(), hash, FILE.size(), FILE.contentType()), NOW.minusSeconds(300));
    }
}
