package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.InboxService;

/**
 * Приём одного сообщения: отметка во входящих решает, сохранять ли сырьё. Общую транзакцию
 * этих двух записей unit-тест не видит — её проверяет путь через брокер
 * ({@code OneCQueueIngestTest}); здесь — порядок решения и исход для слушателя.
 */
@ExtendWith(MockitoExtension.class)
class OneCIngestServiceTest {

    private static final UUID MESSAGE_ID = UUID.fromString("9f1c0f2e-0000-4000-8000-00000000000b");
    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final String PAYLOAD = "{\"data\":{},\"metadata\":{}}";

    @Mock
    private InboxService inboxService;

    @Mock
    private InboundMessageService inboundMessageService;

    @Test
    void newMessage_isStoredWithItsStreamAndQueue() {
        when(inboxService.registerIfNew(MESSAGE_ID)).thenReturn(true);

        boolean stored = service().receive(MESSAGE_ID, OneCStream.MKB10, QUEUE, PAYLOAD);

        assertThat(stored).isTrue();
        verify(inboundMessageService).store(MESSAGE_ID, OneCStream.MKB10, QUEUE, PAYLOAD);
    }

    /** Дубль не сохраняется второй раз: сырьё под этим ключом уже лежит. */
    @Test
    void knownMessage_isNotStoredAgain() {
        when(inboxService.registerIfNew(MESSAGE_ID)).thenReturn(false);

        boolean stored = service().receive(MESSAGE_ID, OneCStream.MKB10, QUEUE, PAYLOAD);

        assertThat(stored).isFalse();
        verifyNoInteractions(inboundMessageService);
    }

    private OneCIngestService service() {
        return new OneCIngestService(inboxService, inboundMessageService);
    }
}
