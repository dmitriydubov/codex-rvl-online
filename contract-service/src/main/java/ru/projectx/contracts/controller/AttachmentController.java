package ru.projectx.contracts.controller;

import jakarta.servlet.http.Part;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import ru.projectx.contracts.dto.AttachmentReceipt;
import ru.projectx.contracts.service.storage.AttachmentUpload;
import ru.projectx.contracts.service.storage.AttachmentUploads;

@InternalApi
public class AttachmentController {

    private final AttachmentUploads uploads;

    public AttachmentController(AttachmentUploads uploads) {
        this.uploads = uploads;
    }

    @PostMapping(path = "/requests/{individual_request_id}/participants/{participant_id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentReceipt upload(
            @PathVariable("individual_request_id") UUID individualRequestId,
            @PathVariable("participant_id") UUID participantId,
            @RequestPart("attachment_id") Part attachmentId,
            @RequestPart("file") Part file) throws IOException {
        return uploads.receive(AttachmentUpload.certificate(individualRequestId, participantId,
                AttachmentParts.id(attachmentId), AttachmentParts.file(file)));
    }

    @PostMapping(path = "/policies/{policy_id}/family-members/{family_member_id}/consents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentReceipt uploadConsent(
            @PathVariable("policy_id") UUID policyId,
            @PathVariable("family_member_id") UUID familyMemberId,
            @RequestPart("attachment_id") Part attachmentId,
            @RequestPart("file") Part file) throws IOException {
        return uploads.receive(AttachmentUpload.consent(policyId, familyMemberId,
                AttachmentParts.id(attachmentId), AttachmentParts.file(file)));
    }
}
