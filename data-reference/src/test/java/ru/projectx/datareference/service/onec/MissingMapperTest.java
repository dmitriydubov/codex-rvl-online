package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.InboundMessageService;
import tools.jackson.databind.json.JsonMapper;

class MissingMapperTest {

    @Test
    void streamWithoutMapper_isSkippedWithReason() {
        InboundMessageService inboundMessageService = mock(InboundMessageService.class);
        ReferenceImportService importService = new ReferenceImportService(
                List.of(), List.of(), inboundMessageService, mock(SupersededParentWaits.class),
                JsonMapper.builder().build(),
                mock(ApplicationEventPublisher.class));

        UUID messageId = UUID.randomUUID();
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(messageId, OneCStream.MKB10, "очередь", Instant.now()),
                envelope("{\"id\":\"" + messageId + "\"}"));

        ImportOutcome result = importService.importMessage(message);

        assertThat(result).isInstanceOf(ImportOutcome.Skipped.class);
        assertThat(((ImportOutcome.Skipped) result).reason()).contains("маппера для потока MKB10 ещё нет");
        verify(inboundMessageService).markSkipped(messageId, ((ImportOutcome.Skipped) result).reason());
    }
}
