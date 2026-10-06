package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.repository.InboundMessageRepository;

/** Защищает чтение частей передачи заранее: читаются все сообщения передачи, в любом статусе. */
@ExtendWith(MockitoExtension.class)
class PriceTransferPartsReaderTest {

    private static final UUID CID = UUID.fromString("8e4b0f70-0000-4000-8000-00000000000c");

    @Mock
    private InboundMessageRepository inbound;

    @Test
    void readsAllMessagesOfTransfer() {
        InboundMessage part = InboundMessage.receive(
                new InboundReceipt(UUID.randomUUID(), OneCStream.PRICE_LISTS, "очередь", Instant.EPOCH), "{}");
        when(inbound.findByCorrelationId(CID)).thenReturn(List.of(part));

        assertThat(new PriceTransferPartsReader(inbound).parts(CID)).containsExactly(part);
    }
}
