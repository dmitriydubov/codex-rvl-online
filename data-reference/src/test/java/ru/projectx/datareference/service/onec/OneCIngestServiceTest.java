package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.entity.inbound.OneCStream;
import ru.projectx.datareference.service.inbound.InboundMessageService;
import ru.projectx.datareference.service.inbound.InboxService;
import ru.projectx.datareference.service.leadership.LeadershipFence;
import ru.projectx.datareference.service.leadership.LeadershipLostException;

@ExtendWith(MockitoExtension.class)
class OneCIngestServiceTest {

    private static final UUID MESSAGE_ID = UUID.fromString("9f1c0f2e-0000-4000-8000-00000000000b");
    private static final String QUEUE = "1c-usk_to_rvl-online_mkb10-changes";
    private static final String PAYLOAD = "{\"data\":{},\"metadata\":{}}";

    @Mock
    private InboxService inboxService;

    @Mock
    private InboundMessageService inboundMessageService;

    private final LeadershipFence fence = mock(LeadershipFence.class);

    @Test
    void newMessage_isStoredWithItsStreamAndQueue() {
        when(inboxService.registerIfNew(MESSAGE_ID)).thenReturn(true);

        boolean stored = service().receive(MESSAGE_ID, OneCStream.MKB10, QUEUE, PAYLOAD);

        assertThat(stored).isTrue();
        verify(inboundMessageService).store(MESSAGE_ID, OneCStream.MKB10, QUEUE, PAYLOAD);
    }

    @Test
    void knownMessage_isNotStoredAgain() {
        when(inboxService.registerIfNew(MESSAGE_ID)).thenReturn(false);

        boolean stored = service().receive(MESSAGE_ID, OneCStream.MKB10, QUEUE, PAYLOAD);

        assertThat(stored).isFalse();
        verifyNoInteractions(inboundMessageService);
    }

    @Test
    void instanceThatLostTheLeaderLock_storesNothing() {
        doThrow(new LeadershipLostException("0b5f3c1e-7a2d-4e8b-9c6f-1d2e3f4a5b6c")).when(fence).holdUntilCommit();

        assertThatThrownBy(() -> service().receive(MESSAGE_ID, OneCStream.MKB10, QUEUE, PAYLOAD))
                .isInstanceOf(LeadershipLostException.class);

        verifyNoInteractions(inboxService, inboundMessageService);
    }

    private OneCIngestService service() {
        return new OneCIngestService(inboxService, inboundMessageService, fence);
    }
}
