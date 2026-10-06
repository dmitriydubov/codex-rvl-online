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
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.service.InboundMessageService;
import tools.jackson.databind.json.JsonMapper;

/**
 * Поток, у которого маппера нет, — это пропуск с причиной, а не отказ и не обращение
 * к пустой ссылке.
 *
 * <p>Проверяется без контекста и без базы, потому что воспроизвести случай иначе уже
 * нечем: маппер есть у каждого потока. Ветка при этом не лишняя — она страхует ровно
 * тот момент, когда в перечень потоков добавят новый, а маппер к нему написать забудут.
 */
class MissingMapperTest {

    @Test
    void streamWithoutMapper_isSkippedWithReason() {
        InboundMessageService inboundMessageService = mock(InboundMessageService.class);
        ReferenceImportService importService = new ReferenceImportService(
                List.of(), List.of(), inboundMessageService, JsonMapper.builder().build(),
                mock(ApplicationEventPublisher.class));

        UUID messageId = UUID.randomUUID();
        InboundMessage message = InboundMessage.receive(
                new InboundReceipt(messageId, OneCStream.MKB10, "очередь", Instant.now()),
                envelope("{\"id\":\"" + messageId + "\"}"));

        ImportResult result = importService.importMessage(message);

        assertThat(result).isInstanceOf(ImportResult.Skipped.class);
        assertThat(((ImportResult.Skipped) result).reason()).contains("маппера для потока MKB10 ещё нет");
        verify(inboundMessageService).markSkipped(messageId, ((ImportResult.Skipped) result).reason());
    }
}
