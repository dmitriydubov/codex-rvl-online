package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.mapper.StreamMapper;
import ru.projectx.datareference.service.InboundMessageService;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает обычный путь сообщения: маппер потока разбирает его, а исход записывается в ту же строку сырья.
 *
 * <p>Если исход не записать, строка останется новой и будет разобрана снова при следующем проходе. Если
 * записать не тот, справочник и статус сырья разойдутся: применённое сообщение будет выглядеть пропущенным
 * или наоборот.
 */
@ExtendWith(MockitoExtension.class)
class MappedStreamHandlerTest {

    private static final UUID MESSAGE_ID = UUID.fromString("7d2b1f0e-0000-4000-8000-0000000000aa");

    @Mock
    private StreamMapper mapper;

    @Mock
    private InboundMessageService inboundMessageService;

    private final ObjectNode data = JsonMapper.builder().build().createObjectNode().put("code", "A00");

    @Test
    void handlesTheStreamOfItsMapper() {
        when(mapper.stream()).thenReturn(OneCStream.MKB10);

        assertThat(handler().stream()).isEqualTo(OneCStream.MKB10);
    }

    @Test
    void appliedMessage_isMarkedProcessed() {
        when(mapper.apply(data)).thenReturn(ImportResult.applied());

        ImportResult result = handler().handle(message(), data);

        assertThat(result).isEqualTo(ImportResult.applied());
        verify(inboundMessageService).markProcessed(MESSAGE_ID);
        verifyNoMoreInteractions(inboundMessageService);
    }

    @Test
    void skippedMessage_isMarkedSkippedWithItsReason() {
        when(mapper.apply(data)).thenReturn(ImportResult.skipped("не санаторий"));

        ImportResult result = handler().handle(message(), data);

        assertThat(result).isEqualTo(ImportResult.skipped("не санаторий"));
        verify(inboundMessageService).markSkipped(MESSAGE_ID, "не санаторий");
        verifyNoMoreInteractions(inboundMessageService);
    }

    /**
     * Отклонённое маппером сообщение уходит по общему пути окончательного отказа: разбор откатывается, а отказ
     * записывают после отката с записью в журнал и причиной от маппера.
     */
    @Test
    void rejectedMessage_isContractViolationWithItsReason() {
        when(mapper.apply(data)).thenReturn(ImportResult.rejected("запись отклонена"));

        assertThatThrownBy(() -> handler().handle(message(), data))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("запись отклонена");
        verifyNoInteractions(inboundMessageService);
    }

    private MappedStreamHandler handler() {
        return new MappedStreamHandler(mapper, inboundMessageService);
    }

    private static InboundMessage message() {
        return InboundMessage.receive(new InboundReceipt(MESSAGE_ID, OneCStream.MKB10, "очередь", Instant.EPOCH), "{}");
    }
}
