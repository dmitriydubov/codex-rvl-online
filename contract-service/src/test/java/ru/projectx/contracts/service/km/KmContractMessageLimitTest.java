package ru.projectx.contracts.service.km;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.util.unit.DataSize;
import ru.projectx.contracts.config.KmContractSubmissionProperties;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.MessageAttachment;
import ru.projectx.contracts.entity.StoredFile;
import ru.projectx.contracts.exception.MessageTooLargeException;
import ru.projectx.contracts.repository.MessageAttachmentRepository;
import ru.projectx.contracts.service.outbox.OutboundAttachment;
import ru.projectx.contracts.service.outbox.OutboundBodies;
import ru.projectx.contracts.service.outbox.OutboundBody;
import ru.projectx.contracts.service.storage.AttachmentMetrics;
import ru.projectx.contracts.service.storage.CertificateStore;
import ru.projectx.contracts.support.Issuances;
import ru.projectx.contracts.support.Tasks;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class KmContractMessageLimitTest {

    private static final String PAYLOAD = "{\"data\": {\"number\": \"ДКМ\"}}";
    private static final UUID MESSAGE_ID = UUID.fromString("0196c0f1-7a2b-7c3d-8e4f-5a6b7c8d9e0f");

    private final KmContractMessageLimit limit = new KmContractMessageLimit(properties(65536));

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 4", "2, 4", "3, 4", "4, 8", "20000, 26668", "50000, 66668"})
    void base64Length_isFourBytesPerStartedBlockOfThree(long bytes, long encoded) {
        assertThat(KmContractMessageLimit.base64Length(bytes)).isEqualTo(encoded);
    }

    @Test
    void size_isTheUtf8BodyPlusTheContentFieldAndBase64OfEveryCertificate() {
        long body = PAYLOAD.getBytes(StandardCharsets.UTF_8).length;

        assertThat(limit.size(PAYLOAD, List.of())).isEqualTo(body);
        assertThat(limit.size(PAYLOAD, List.of(certificate(20000), certificate(20000))))
                .isEqualTo(body + 2 * (20 + 26668));
    }

    @Test
    void messageWithinTheLimit_passes() {
        assertThatCode(() -> limit.verify(PAYLOAD, List.of(certificate(20000), certificate(20000))))
                .doesNotThrowAnyException();
    }

    @Test
    void messageExactlyAtTheLimit_passes() {
        long body = PAYLOAD.getBytes(StandardCharsets.UTF_8).length;
        KmContractMessageLimit exact = new KmContractMessageLimit(properties(body + 20 + 4));

        assertThatCode(() -> exact.verify(PAYLOAD, List.of(certificate(3)))).doesNotThrowAnyException();
        assertThatThrownBy(() -> exact.verify(PAYLOAD, List.of(certificate(4))))
                .isInstanceOf(MessageTooLargeException.class);
    }

    @Test
    void messageOverTheLimit_isRefusedWithTheLimitAndTheNumberOfCertificatesAndWithoutTheirNames() {
        assertThatThrownBy(() -> limit.verify(PAYLOAD, List.of(certificate(30000), certificate(30000))))
                .isInstanceOf(MessageTooLargeException.class)
                .satisfies(failure -> {
                    MessageTooLargeException tooLarge = (MessageTooLargeException) failure;
                    assertThat(tooLarge.limit()).isEqualTo(65536);
                    assertThat(tooLarge.documents()).isEqualTo(2);
                    assertThat(tooLarge.getMessage()).doesNotContain("certificates/");
                });
    }

    @Test
    void computedSize_matchesTheBodyThatIsPublishedWithTheContentsFilledIn() {
        Contract contract = Issuances.paidContract(KmContractSubmissionMessagesTest.CONTRACT_ID, Tasks.SPOUSE,
                "DKM-2026-000001", KmContractSubmissionMessagesTest.PAID_AT);
        JsonMapper json = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        KmContractSubmissionMessages messages = new KmContractSubmissionMessages(json,
                KmContractSubmissionMessagesTest.properties());
        List<OutboundAttachment> certificates = List.of(KmContractSubmissionMessagesTest.JPEG,
                KmContractSubmissionMessagesTest.PDF);
        String payload = messages.write(messages.meta(contract, KmContractSubmissionMessagesTest.NOW), contract,
                KmContractSubmissionMessagesTest.facts(certificates));
        MessageAttachmentRepository attachments = mock();
        given(attachments.findByOutboxMessageIdOrderByCreatedAtAscIdAsc(MESSAGE_ID)).willReturn(certificates.stream()
                .map(certificate -> MessageAttachment.of(MESSAGE_ID, certificate.documentId(), certificate.file(),
                        KmContractSubmissionMessagesTest.NOW))
                .toList());
        CertificateStore store = mock();
        given(store.read(KmContractSubmissionMessagesTest.JPEG.file().key())).willReturn(new byte[76]);
        given(store.read(KmContractSubmissionMessagesTest.PDF.file().key())).willReturn(new byte[20000]);

        OutboundBody published = new OutboundBodies(attachments, store, mock(AttachmentMetrics.class), json)
                .complete(MESSAGE_ID, payload);

        assertThat(published).isInstanceOf(OutboundBody.Complete.class);
        String sent = ((OutboundBody.Complete) published).payload();
        assertThat(sent.getBytes(StandardCharsets.UTF_8).length).isEqualTo(limit.size(payload, certificates));
    }

    private static OutboundAttachment certificate(long size) {
        return new OutboundAttachment(UUID.randomUUID(), new StoredFile("certificates/0b6c/" + size + ".jpg",
                "0".repeat(64), size, "image/jpeg"));
    }

    private static KmContractSubmissionProperties properties(long maxMessageSize) {
        return new KmContractSubmissionProperties(Duration.ofSeconds(10), 100, DataSize.ofBytes(maxMessageSize),
                Duration.ofMinutes(1), Duration.ofHours(1));
    }
}
