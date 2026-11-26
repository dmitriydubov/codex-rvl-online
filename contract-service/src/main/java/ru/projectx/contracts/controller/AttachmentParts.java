package ru.projectx.contracts.controller;

import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import ru.projectx.contracts.service.storage.AttachmentContent;

final class AttachmentParts {

    private static final int UUID_LENGTH = 36;
    private static final String IDENTIFIER = "attachment_id";

    private AttachmentParts() {
    }

    static UUID id(Part part) throws IOException {
        if (part.getSubmittedFileName() != null) {
            throw new InvalidAttachmentFieldException(IDENTIFIER);
        }
        try (InputStream stream = part.getInputStream()) {
            String value = new String(stream.readNBytes(UUID_LENGTH + 1), StandardCharsets.US_ASCII);
            return AttachmentIds.parse(IDENTIFIER, value);
        }
    }

    static AttachmentContent file(Part part) {
        String name = part.getSubmittedFileName();
        if (name == null || name.isBlank()) {
            throw new InvalidAttachmentFieldException(ApiErrors.FILE_FIELD);
        }
        return new AttachmentContent(part.getContentType(), part.getSize(), part::getInputStream);
    }
}
