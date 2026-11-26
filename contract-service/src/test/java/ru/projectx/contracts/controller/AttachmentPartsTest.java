package ru.projectx.contracts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockPart;
import ru.projectx.contracts.service.storage.AttachmentContent;

class AttachmentPartsTest {

    private static final String ID = "3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21";
    private static final byte[] ID_BYTES = ID.getBytes(StandardCharsets.US_ASCII);

    @Test
    void bareUuidPart_isDecoded() throws Exception {
        assertThat(AttachmentParts.id(new MockPart("attachment_id", ID_BYTES))).isEqualTo(UUID.fromString(ID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "stand.txt"})
    void attachmentIdWithAnyFilename_isRejected(String filename) {
        assertThatThrownBy(() -> AttachmentParts.id(new MockPart("attachment_id", filename, ID_BYTES)))
                .isInstanceOfSatisfying(InvalidAttachmentFieldException.class,
                        failure -> assertThat(failure.field()).isEqualTo("attachment_id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21x", "3c2f8e10-5a7b-4d21-9f6e-xb0a7c4d8e21"})
    void malformedUuid_isRejected(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> AttachmentParts.id(new MockPart("attachment_id", bytes)))
                .isInstanceOfSatisfying(InvalidAttachmentFieldException.class,
                        failure -> assertThat(failure.field()).isEqualTo("attachment_id"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingOrBlankFilename_isRejected(String filename) {
        assertThatThrownBy(() -> AttachmentParts.file(new MockPart("file", filename, ID_BYTES)))
                .isInstanceOfSatisfying(InvalidAttachmentFieldException.class,
                        failure -> assertThat(failure.field()).isEqualTo("file"));
    }

    @Test
    void namedFile_returnsTheOriginalMetadataAndStream() throws Exception {
        AttachmentContent file = AttachmentParts.file(new MockPart("file", "test.pdf", ID_BYTES, MediaType.APPLICATION_PDF));

        assertThat(file.contentType()).isEqualTo("application/pdf");
        assertThat(file.size()).isEqualTo(ID_BYTES.length);
        assertThat(file.source().open()).hasBinaryContent(ID_BYTES);
    }
}
