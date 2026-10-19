package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ru.projectx.datareference.entity.inbound.InboundMessage;
import ru.projectx.datareference.entity.inbound.InboundStatus;
import ru.projectx.datareference.entity.catalog.Mkb10;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.repository.catalog.Mkb10Repository;
import ru.projectx.datareference.service.inbound.InboundMessageService;
import ru.projectx.datareference.support.DatabaseCleaner;
import ru.projectx.datareference.support.IntegrationTest;

@IntegrationTest
class ReferenceImportServiceTest {

    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final UUID CODE_ID = UUID.fromString("7d2b1f0e-0000-4000-8000-000000000001");

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private ReferenceImportService importService;

    @Autowired
    private InboundMessageService inboundMessageService;

    @Autowired
    private InboundMessageRepository inboundMessageRepository;

    @Autowired
    private Mkb10Repository mkb10Repository;

    @BeforeEach
    void clearTables() {
        databaseCleaner.clear();
    }

    @Test
    void message_becomesReferenceRow() {
        UUID messageId = store(OneCStream.MKB10, mkb10("A00", "Холера"));

        importService.importMessage(message(messageId));

        Mkb10 stored = mkb10Repository.findByExternalId(CODE_ID).orElseThrow();
        assertThat(stored.code()).isEqualTo("A00");
        assertThat(stored.name()).isEqualTo("Холера");
        assertThat(stored.isArchive()).isFalse();
    }

    @Test
    void appliedMessage_isMarkedProcessed() {
        UUID messageId = store(OneCStream.MKB10, mkb10("A00", "Холера"));

        importService.importMessage(message(messageId));

        assertThat(inboundMessageRepository.findById(messageId).orElseThrow().outcome().status())
                .isEqualTo(InboundStatus.PROCESSED);
    }

    @Test
    void repeatedMessage_updatesRow() {
        importService.importMessage(message(store(OneCStream.MKB10, mkb10("A00", "Холера"))));

        importService.importMessage(message(store(OneCStream.MKB10, mkb10("A00", "Холера уточнённая"))));

        assertThat(mkb10Repository.findByExternalId(CODE_ID).orElseThrow().name())
                .isEqualTo("Холера уточнённая");
    }

    @Test
    void lastAppliedMessageWins() {
        importService.importMessage(message(store(OneCStream.MKB10, mkb10("A00", "Холера уточнённая"))));
        UUID staleId = store(OneCStream.MKB10, mkb10("A00", "Холера"));

        ImportOutcome result = importService.importMessage(message(staleId));

        assertThat(result).isInstanceOf(ImportOutcome.Applied.class);
        assertThat(mkb10Repository.findByExternalId(CODE_ID).orElseThrow().name())
                .isEqualTo("Холера");
    }

    @Test
    void skippedMessage_keepsReasonInRawRow() {
        UUID skippedId = store(OneCStream.CONTRAGENTS, insurer());

        importService.importMessage(message(skippedId));

        InboundMessage skipped = inboundMessageRepository.findById(skippedId).orElseThrow();
        assertThat(skipped.outcome().status()).isEqualTo(InboundStatus.SKIPPED);
        assertThat(skipped.outcome().lastError()).isNotBlank();
    }

    @Test
    void envelopeWithoutData_isContractViolation() {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, OneCStream.MKB10, QUEUE, "{\"metadata\":{\"producer\":\"1c-usk\"}}");

        assertThatThrownBy(() -> importService.importMessage(message(messageId)))
                .isInstanceOf(OneCFieldException.class)
                .hasMessageContaining("data");
    }

    private UUID store(OneCStream stream, String data) {
        UUID messageId = UUID.randomUUID();
        inboundMessageService.store(messageId, stream, QUEUE, envelope(data));
        return messageId;
    }

    private InboundMessage message(UUID messageId) {
        return inboundMessageRepository.findById(messageId).orElseThrow();
    }

    private static String insurer() {
        return """
                {"guid":"3f0a9c11-0000-4000-8000-000000000002","thisIsLPU":false,\
                "Наименование":"АО «Работодатель»","ИНН":"7701234567"}""";
    }

    private static String mkb10(String code, String name) {
        return """
                {"id":"%s","code":"%s","name":"%s","hierarchyLevels":1}"""
                .formatted(CODE_ID, code, name);
    }
}
