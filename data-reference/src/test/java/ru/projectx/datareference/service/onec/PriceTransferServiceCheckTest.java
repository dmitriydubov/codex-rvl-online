package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ru.projectx.datareference.entity.InboundStatus;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.InboundMessageRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает правило «передача прайса — это прайс одной услуги»: часть, которая называет другую услугу, чем части
 * передачи в буфере, или не называет её читаемым GUID, — ошибка части; поздняя часть передачи с записанным исходом
 * не проверяется.
 */
class PriceTransferServiceCheckTest {

    private static final UUID CID = UUID.fromString("8e4b0f70-0000-4000-8000-00000000000c");
    private static final UUID SERVICE = UUID.fromString("8e4b0f70-0000-4000-8000-000000000003");
    private static final UUID OTHER_SERVICE = UUID.fromString("8e4b0f70-0000-4000-8000-000000000004");
    private static final String MIXED = "части передачи " + CID + " называют разные услуги";

    private final InboundMessageRepository inbound = mock(InboundMessageRepository.class);
    private final PriceTransferServiceCheck check = new PriceTransferServiceCheck(inbound);
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void partNamingTheServiceOfBufferedParts_namesIt_inAnyCase() {
        bufferedParts(SERVICE.toString().toUpperCase(), SERVICE.toString());

        assertThat(check.serviceOf(CID, part(SERVICE.toString()))).contains(SERVICE);
    }

    @Test
    void bufferedPartWithoutReadableService_makesTransferMixed() {
        bufferedParts(SERVICE.toString(), null);

        assertThatThrownBy(() -> check.serviceOf(CID, part(SERVICE.toString())))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage(MIXED);
    }

    @Test
    void partWithEmptyGuidAsService_isDefect() {
        assertThatThrownBy(() -> check.serviceOf(CID, part("00000000-0000-0000-0000-000000000000")))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("нет обязательного GUID в поле keyValue");
    }

    @Test
    void partWithServiceNotWrittenAsGuid_isDefect() {
        assertThatThrownBy(() -> check.serviceOf(CID, part("1-2-3-4-5")))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("поле keyValue не является GUID");
    }

    @Test
    void outcomeIsRecordedByAppliedSkippedOrFailedParts() {
        check.serviceOf(CID, part(SERVICE.toString()));

        verify(inbound).existsByCorrelationIdAndStatusIn(CID,
                EnumSet.of(InboundStatus.PROCESSED, InboundStatus.SKIPPED, InboundStatus.FAILED));
    }

    /** GUID услуги в сырье части читается по тому же правилу, что и в пришедшей: пробелы по краям не в счёт. */
    @Test
    void bufferedServiceWithSpacesAround_isTheSameService() {
        bufferedParts(" " + SERVICE + " ");

        assertThat(check.serviceOf(CID, part(SERVICE.toString()))).contains(SERVICE);
    }

    @Test
    void partNamingAnotherServiceThanBufferedParts_isDefect() {
        bufferedParts(SERVICE.toString());

        assertThatThrownBy(() -> check.serviceOf(CID, part(OTHER_SERVICE.toString())))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage(MIXED);
    }

    @Test
    void partOfTransferWithRecordedOutcome_namesItsReadableService_orNone() {
        outcomeRecorded();

        assertThat(check.serviceOf(CID, part(OTHER_SERVICE.toString()))).contains(OTHER_SERVICE);
        assertThat(check.serviceOf(CID, part("1-2-3-4-5"))).isEmpty();
        verify(inbound, never()).findBufferedTransferServices(CID);
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
