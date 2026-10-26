package ru.projectx.contracts.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageAttachmentTest {

    private static final Instant NOW = Instant.parse("2026-02-10T07:32:00Z");
    private static final UUID MESSAGE = UUID.fromString("0196c0f1-7a2b-7c3d-8e4f-5a6b7c8d9e0f");
    private static final UUID DOCUMENT = UUID.fromString("0f4b8e2a-6c13-4d7e-b9a1-2e5c7d3f8a64");
    private static final StoredFile FILE = new StoredFile(
            "certificates/0b6c1d2e-7f3a-4b5c-9d8e-1f2a3b4c5d6e/3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21.jpg",
            "7851d78af0fb98bbbda8933f49860a009ac7761c21a1136613c8a6704b156251", 76L, "image/jpeg");

    @Test
    void attachment_pointsToTheMessageAndTheDocumentAndCarriesOnlyTheObjectReference() {
        MessageAttachment attachment = MessageAttachment.of(MESSAGE, DOCUMENT, FILE, NOW);

        assertThat(attachment.documentId()).isEqualTo(DOCUMENT);
        assertThat(attachment.fileKey()).isEqualTo(FILE.key());
        assertThat(attachment.contentType()).isEqualTo("image/jpeg");
        assertThat(attachment.fileSize()).isEqualTo(76L);
        assertThat(attachment).hasFieldOrPropertyWithValue("outboxMessageId", MESSAGE);
        assertThat(attachment).hasFieldOrPropertyWithValue("createdAt", NOW);
        assertThat(attachment).hasFieldOrPropertyWithValue("id", null);
    }
}
