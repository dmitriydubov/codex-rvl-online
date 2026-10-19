package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ru.projectx.datareference.entity.inbound.InboundStatus;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.inbound.InboundMessageRepository;
import ru.projectx.datareference.repository.inbound.PriceListFinalizationRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PriceTransferServiceLockTest {

    private static final UUID CID = UUID.fromString("8e4b0f70-0000-4000-8000-00000000000c");
    private static final UUID SERVICE = UUID.fromString("8e4b0f70-0000-4000-8000-000000000003");
    private static final UUID OTHER_SERVICE = UUID.fromString("8e4b0f70-0000-4000-8000-000000000004");
    private static final String MIXED = "части передачи " + CID + " называют разные услуги";

    private final InboundMessageRepository inbound = mock(InboundMessageRepository.class);
    private final PriceListFinalizationRepository finalizations = mock(PriceListFinalizationRepository.class);
    private final PriceTransferServiceLock lock =
            new PriceTransferServiceLock(inbound, finalizations);
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void firstPartOfTransfer_locksItsService() {
        lock.lockServiceOf(CID, part(SERVICE.toString()));

        verify(finalizations).lockByService(SERVICE);
    }

    @Test
    void serviceIsCheckedAgainstBufferedParts_beforeItsRowIsLocked() {
        bufferedParts(SERVICE.toString());

        lock.lockServiceOf(CID, part(SERVICE.toString()));

        InOrder steps = inOrder(inbound, finalizations);
        steps.verify(inbound).findBufferedTransferServices(CID);
        steps.verify(finalizations).lockByService(SERVICE);
    }

    @Test
    void partNamingAnotherServiceThanBufferedParts_isDefect_andLocksNothing() {
        bufferedParts(SERVICE.toString(), SERVICE.toString());

        assertThatThrownBy(() -> lock.lockServiceOf(CID, part(OTHER_SERVICE.toString())))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage(MIXED);
        verify(finalizations, never()).lockByService(any());
    }

    @Test
    void partWithoutService_isDefect() {
        assertThatThrownBy(() -> lock.lockServiceOf(CID, json.createObjectNode()))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного GUID в поле keyValue");
        verify(finalizations, never()).lockByService(any());
    }

    @Test
    void partOfTransferWithRecordedOutcome_isNotChecked_andLocksItsReadableService() {
        outcomeRecorded();

        lock.lockServiceOf(CID, part(OTHER_SERVICE.toString()));

        verify(finalizations).lockByService(OTHER_SERVICE);
        verify(inbound, never()).findBufferedTransferServices(CID);
    }

    @Test
    void partWithoutReadableServiceOfTransferWithRecordedOutcome_locksNothing() {
        outcomeRecorded();

        lock.lockServiceOf(CID, part("1-2-3-4-5"));
        lock.lockServiceOf(CID, json.createObjectNode());

        verify(finalizations, never()).lockByService(any());
    }

    @Test
    void transferService_isTheServiceOfBufferedParts() {
        when(inbound.findBufferedTransferService(CID)).thenReturn(Optional.of(SERVICE.toString()));

        lock.lockTransferService(CID, part(OTHER_SERVICE.toString()));

        verify(finalizations).lockByService(SERVICE);
        verify(finalizations, never()).lockByService(OTHER_SERVICE);
    }

    @Test
    void transferWithoutBufferedParts_locksServiceOfThePart() {
        when(inbound.findBufferedTransferService(CID)).thenReturn(Optional.empty());

        lock.lockTransferService(CID, part(SERVICE.toString()));

        verify(finalizations).lockByService(SERVICE);
    }

    @Test
    void transferWithoutReadableService_locksNothing() {
        when(inbound.findBufferedTransferService(CID)).thenReturn(Optional.of("не GUID"));

        lock.lockTransferService(CID, json.createObjectNode());
        lock.lockTransferService(CID, part("не GUID"));

        verify(finalizations, never()).lockByService(any());
    }

    private void outcomeRecorded() {
        when(inbound.existsByCorrelationIdAndStatusIn(
                CID, EnumSet.of(InboundStatus.PROCESSED, InboundStatus.SKIPPED, InboundStatus.FAILED)))
                .thenReturn(true);
    }

    private void bufferedParts(String... services) {
        when(inbound.findBufferedTransferServices(CID)).thenReturn(Arrays.asList(services));
    }

    private ObjectNode part(String service) {
        ObjectNode part = json.createObjectNode();
        part.put("keyValue", service);
        return part;
    }
}
