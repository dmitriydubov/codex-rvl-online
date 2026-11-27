package ru.projectx.contracts.controller;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import ru.projectx.contracts.exception.InvalidAttachmentIdException;
import ru.projectx.contracts.service.Guids;
import ru.projectx.contracts.service.storage.AttachmentContent;
import ru.projectx.contracts.service.storage.AttachmentContentReads;
import ru.projectx.contracts.service.storage.AttachmentDeletes;

@InternalApi
public class AttachmentContentController {

    static final String ID_FIELD = "attachment_id";
    private static final String ATTACHMENT = "/attachments/{" + ID_FIELD + "}";

    private final AttachmentContentReads reads;
    private final AttachmentDeletes deletes;

    public AttachmentContentController(AttachmentContentReads reads, AttachmentDeletes deletes) {
        this.reads = reads;
        this.deletes = deletes;
    }

    @GetMapping(ATTACHMENT + "/content")
    public void download(@PathVariable(ID_FIELD) String attachmentId, WebRequest request, HttpServletResponse response)
            throws IOException {
        AttachmentContent file = reads.open(requireId(attachmentId));
        try (InputStream stream = file.stream()) {
            String etag = "\"" + file.hash() + "\"";
            if (!request.checkNotModified(etag)) {
                response.setContentType(file.contentType());
                response.setContentLengthLong(file.size());
                stream.transferTo(response.getOutputStream());
            }
        } catch (IOException failure) {
            throw transferFailure(failure);
        }
    }

    @DeleteMapping(ATTACHMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable(ID_FIELD) String attachmentId) {
        deletes.delete(requireId(attachmentId));
    }

    private static UUID requireId(String value) {
        return Guids.canonical(value).orElseThrow(InvalidAttachmentIdException::new);
    }

    private static IOException transferFailure(IOException failure) {
        return new IOException("не удалось передать содержимое вложения: " + failure.getClass().getSimpleName());
    }
}
