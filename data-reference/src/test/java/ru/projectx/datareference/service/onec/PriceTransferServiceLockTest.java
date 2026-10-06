package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
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
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.InboundMessageRepository;
import ru.projectx.datareference.repository.PriceListFinalizationRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает правило «передача прайса — это прайс одной услуги» и выбор строки услуги, которую блокирует часть
 * передачи: часть, которая называет другую услугу, чем части в буфере, или не называет её читаемым GUID, отклоняется
 * раньше, чем что-то заблокировано, а отказ передачи блокирует ту же услугу, что и проверка зависших передач.
 */
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

    /** Услуга сверяется с частями в буфере раньше, чем блокируется её строка. */
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

    /** Часть в буфере без читаемого GUID услуги не называет ту же услугу: собрать передачу по одной услуге нельзя. */
    @Test
    void partWithoutService_isDefect() {
        assertThatThrownBy(() -> lock.lockServiceOf(CID, json.createObjectNode()))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного GUID в поле keyValue");
        verify(finalizations, never()).lockByService(any());
    }

    /** Исход передачи уже записан: поздняя часть разделяет его, и какую услугу она называет, не проверяется. */
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

    /** Исход записан, если есть хотя бы одна часть применённая, пропущенная или отклонённая. */
    /**
     * При отказе блокируется услуга, которую называют части передачи в буфере, — та же, что блокирует проверка
     * зависших передач, даже если отклоняемая часть называет другую.
     */
    @Test
    void transferService_isTheServiceOfBufferedParts() {
        when(inbound.findBufferedTransferService(CID)).thenReturn(Optional.of(SERVICE.toString()));

        lock.lockTransferService(CID, part(OTHER_SERVICE.toString()));

        verify(finalizations).lockByService(SERVICE);
        verify(finalizations, never()).lockByService(OTHER_SERVICE);
    }

    /** Частей в буфере нет: блокируется услуга, которую называет сама отклоняемая часть. */
    @Test
    void transferWithoutBufferedParts_locksServiceOfThePart() {
        when(inbound.findBufferedTransferService(CID)).thenReturn(Optional.empty());

        lock.lockTransferService(CID, part(SERVICE.toString()));

        verify(finalizations).lockByService(SERVICE);
    }

    /** Ни части в буфере, ни сама часть не называют услугу читаемым GUID: блокировать нечего. */
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
