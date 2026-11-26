package ru.projectx.contracts.service.storage;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class Uploads {

    static final UUID REQUEST = UUID.fromString("61a7982c-a0b4-4bbf-9527-60b40b752803");
    static final UUID PARTICIPANT = UUID.fromString("6778e8c2-985a-4d54-9bd3-0f6403b104d6");
    static final UUID OTHER_PARTICIPANT = UUID.fromString("9275471c-d2fe-484f-97a6-d6d7b1267b5e");
    static final UUID ATTACHMENT = UUID.fromString("3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21");
    static final String CONTENT = "СТЕНД: вымышленная справка 070/у, участник 1";
    static final String CONTENT_HASH = "7851d78af0fb98bbbda8933f49860a009ac7761c21a1136613c8a6704b156251";

    private Uploads() {
    }

    static AttachmentUpload jpeg() {
        return of(PARTICIPANT, "image/jpeg", CONTENT);
    }

    static AttachmentUpload of(UUID participant, @Nullable String contentType, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return AttachmentUpload.certificate(REQUEST, participant, ATTACHMENT,
                new AttachmentContent(contentType, bytes.length, () -> new ByteArrayInputStream(bytes)));
    }

    static AttachmentContent sized(@Nullable String contentType, long size) {
        return new AttachmentContent(contentType, size, () -> new ByteArrayInputStream(new byte[0]));
    }
}
