package ru.projectx.contracts.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.WebRequest;
import ru.projectx.contracts.service.storage.AttachmentContentReads;
import ru.projectx.contracts.service.storage.AttachmentStream;

@InternalApi
public class AttachmentContentController {

    private final AttachmentContentReads contents;

    public AttachmentContentController(AttachmentContentReads contents) {
        this.contents = contents;
    }

    @GetMapping("/attachments/{attachment_id}/content")
    public ResponseEntity<Resource> content(@PathVariable("attachment_id") UUID attachmentId, WebRequest request) {
        AttachmentStream content = contents.openContent(attachmentId);
        if (request.checkNotModified(content.fileHash())) {
            closeStream(content.inputStream());
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.fileSize())
                .eTag(content.fileHash())
                .body(new InputStreamResource(content.inputStream()));
    }

    private static void closeStream(InputStream stream) {
        try {
            stream.close();
        } catch (IOException closing) {
            throw new UncheckedIOException("поток вложения не закрыт", closing);
        }
    }
}
