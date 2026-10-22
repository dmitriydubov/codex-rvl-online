package ru.projectx.contracts.acceptance;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

record AttachmentRequest(
        String request,
        String participant,
        Optional<String> attachmentPart,
        Optional<FilePart> filePart,
        Optional<String> requestId,
        boolean multipart) {

    static final String BOUNDARY = "stand-attachment-boundary";

    record FilePart(String fileName, @Nullable String contentType, byte[] content) {
    }

    AttachmentRequest withRequestId(String value) {
        return new AttachmentRequest(request, participant, attachmentPart, filePart, Optional.of(value), multipart);
    }

    String path() {
        return "/internal/v1/requests/" + request + "/participants/" + participant + "/attachments";
    }

    String contentType() {
        return multipart ? "multipart/form-data; boundary=" + BOUNDARY : "application/json";
    }

    byte[] body() {
        if (!multipart) {
            return ("{\"attachment_id\":\"" + attachmentPart.orElse("") + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        attachmentPart.ifPresent(value -> text(body, "--" + BOUNDARY + "\r\nContent-Disposition: form-data; "
                + "name=\"attachment_id\"\r\n\r\n" + value + "\r\n"));
        filePart.ifPresent(file -> file(body, file));
        text(body, "--" + BOUNDARY + "--\r\n");
        return body.toByteArray();
    }

    private static void file(ByteArrayOutputStream body, FilePart file) {
        text(body, "--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + file.fileName()
                + "\"\r\n" + Optional.ofNullable(file.contentType()).map(type -> "Content-Type: " + type + "\r\n")
                .orElse("") + "\r\n");
        body.writeBytes(file.content());
        text(body, "\r\n");
    }

    private static void text(ByteArrayOutputStream body, String text) {
        body.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
