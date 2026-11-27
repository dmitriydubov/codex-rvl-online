package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.entity.document.DocumentAttachments;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.repository.issuance.IssuanceRequestRepository;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;

class AttachmentRegistryLookupTest {

    @Test
    void findsTheActiveAttachmentByItsIdentifier() {
        var documents = mock(PrintedFormRepository.class);
        var registry = new AttachmentRegistry(mock(IssuanceRequestRepository.class), documents,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var owner = DocumentAttachments.certificate(Uploads.REQUEST, Uploads.PARTICIPANT, Uploads.ATTACHMENT);
        var file = new StoredFile("certificate.jpg", Uploads.CONTENT_HASH, 76, "image/jpeg");
        var attachment = PrintedForm.receivedAttachment(PrintedFormType.MEDICAL_CERTIFICATE, owner, file, Instant.EPOCH);
        when(documents.findActiveAttachment(Uploads.ATTACHMENT)).thenReturn(Optional.of(attachment));

        assertThat(registry.active(Uploads.ATTACHMENT)).containsSame(attachment);
    }
}
