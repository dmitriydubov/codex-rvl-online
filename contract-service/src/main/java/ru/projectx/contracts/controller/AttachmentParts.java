package ru.projectx.contracts.controller;

import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import ru.projectx.contracts.service.storage.AttachmentContent;

final class AttachmentParts {

    private static final int UUID_LENGTH = 36;
    private static final Pattern GUID = Pattern.compile("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE);

    private AttachmentParts() {
    }

    static UUID id(Part part) throws IOException {
        if (part.getSubmittedFileName() != null) {
            throw new InvalidAttachmentPartException("attachment_id");
        }
        try (InputStream stream = part.getInputStream()) {
            String value = new String(stream.readNBytes(UUID_LENGTH + 1), StandardCharsets.US_ASCII);
            if (!GUID.matcher(value).matches()) {
                throw new InvalidAttachmentPartException("attachment_id");
            }
            return UUID.fromString(value);
        }
    }

    static AttachmentContent file(Part part) {
        String name = part.getSubmittedFileName();
        if (name == null || name.isBlank()) {
            throw new InvalidAttachmentPartException(ApiErrors.FILE_FIELD);
        }
        return new AttachmentContent(part.getContentType(), part.getSize(), part::getInputStream);
    }
}
