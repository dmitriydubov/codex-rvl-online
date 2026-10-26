package ru.projectx.contracts.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CertificateDocument(
        UUID documentId,
        String docType,
        String fileName,
        String contentType,
        @Nullable String contentBase64,
        String fileHash) {

    public static final String CONTENT_FIELD = "content_base64";
}
