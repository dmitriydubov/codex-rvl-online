package ru.projectx.datareference.service.onec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static ru.projectx.datareference.support.OneCMessages.envelope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.mapper.StreamMapper;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.OneCMessageApplied;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Защищает выбор пути для сообщения 1С и разбор его конверта.
 *
 * <p>Сообщение уходит обработчику своего потока, и только ему: чужой обработчик применил бы его к чужому
 * справочнику. Обработчик получает поле {@code data} конверта. Конверт, из которого его не достать, — нарушение
 * контракта, а не временный сбой: повтор дал бы то же самое.
 */
@ExtendWith(MockitoExtension.class)
class ReferenceImportDispatchTest {

    private static final UUID MESSAGE_ID = UUID.fromString("7d2b1f0e-0000-4000-8000-0000000000bb");
    private static final String MKB10_DATA = "{\"code\":\"A00\"}";

    private final JsonMapper json = JsonMapper.builder().build();

    @Mock
    private StreamMapper mkb10Mapper;

    @Mock
    private StreamHandler priceListHandler;

    @Mock
    private InboundMessageService inboundMessageService;

    @Mock
    private ApplicationEventPublisher events;

    @Test
    void messageOfMappedStream_goesToItsMapper_withDataOfEnvelope() {
        when(mkb10Mapper.stream()).thenReturn(OneCStream.MKB10);
        when(priceListHandler.stream()).thenReturn(OneCStream.PRICE_LISTS);
        when(mkb10Mapper.apply(any())).thenReturn(ImportResult.applied());

        ImportResult result = service().importMessage(message(OneCStream.MKB10, envelope(MKB10_DATA)));

        assertThat(result).isEqualTo(ImportResult.applied());
        verify(mkb10Mapper).apply(json.readValue(MKB10_DATA, ObjectNode.class));
        verify(inboundMessageService).markProcessed(MESSAGE_ID);
        verify(priceListHandler, never()).handle(any(), any());
        verify(events).publishEvent(new OneCMessageApplied(OneCStream.MKB10));
    }

    @Test
    void messageOfStreamWithOwnHandler_goesToThatHandler() {
        when(mkb10Mapper.stream()).thenReturn(OneCStream.MKB10);
        when(priceListHandler.stream()).thenReturn(OneCStream.PRICE_LISTS);
        InboundMessage message = message(OneCStream.PRICE_LISTS, envelope("{\"cid\":\"c1\"}"));
        when(priceListHandler.handle(any(), any())).thenReturn(ImportResult.skipped("ждёт финального"));

        ImportResult result = service().importMessage(message);

        assertThat(result).isEqualTo(ImportResult.skipped("ждёт финального"));
        verify(priceListHandler).handle(message, json.readValue("{\"cid\":\"c1\"}", ObjectNode.class));
        verify(mkb10Mapper, never()).apply(any());
        verify(events).publishEvent(new OneCMessageApplied(OneCStream.PRICE_LISTS));
    }

    /** Отказ разбора события не публикует: транзакция откатывается, и сбрасывать кэши нечем. */
    @Test
    void failedMessage_publishesNoEvent() {
        when(mkb10Mapper.stream()).thenReturn(OneCStream.MKB10);
        when(priceListHandler.stream()).thenReturn(OneCStream.PRICE_LISTS);
        when(mkb10Mapper.apply(any())).thenThrow(new OneCFieldException("нет кода"));
        ReferenceImportService service = service();

        assertThatThrownBy(() -> service.importMessage(message(OneCStream.MKB10, envelope(MKB10_DATA))))
                .isInstanceOf(OneCFieldException.class);
        verifyNoInteractions(events);
    }

    /** Два пути для одного потока — ошибка сборки приложения: какой из них сработает, решал бы случай. */
    @Test
    void twoHandlersForOneStream_failAtStartup() {
        when(mkb10Mapper.stream()).thenReturn(OneCStream.MKB10);
        when(priceListHandler.stream()).thenReturn(OneCStream.MKB10);

        assertThatThrownBy(this::service).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void erasedPayload_isContractViolation() {
        assertEnvelopeRejected(null, "тело сообщения стёрто по сроку хранения, разбирать нечего");
    }

    /** Конверт разбирается до выбора обработчика: особый путь тоже не получает сообщение со стёртым телом. */
    @Test
    void erasedPayloadOfStreamWithOwnHandler_isRejectedBeforeItsHandler() {
        when(mkb10Mapper.stream()).thenReturn(OneCStream.MKB10);
        when(priceListHandler.stream()).thenReturn(OneCStream.PRICE_LISTS);
        ReferenceImportService service = service();

        assertThatThrownBy(() -> service.importMessage(message(OneCStream.PRICE_LISTS, null)))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("тело сообщения стёрто по сроку хранения, разбирать нечего");
        verify(priceListHandler, never()).handle(any(), any());
    }

    @Test
    void envelopeWithoutData_isContractViolation() {
        assertEnvelopeRejected("{\"metadata\":{}}", "в конверте сообщения нет объекта data");
    }

    @Test
    void dataThatIsNotObject_isContractViolation() {
        assertEnvelopeRejected("{\"data\":[1]}", "в конверте сообщения нет объекта data");
    }

    /**
     * Нечитаемое тело — нарушение контракта. Причина в цепочке называет тип исключения Jackson, но не его текст:
     * Jackson цитирует кусок тела, а в теле полиса бывают персональные данные, которые вывел бы любой стек в журнале.
     */
    @Test
    void bodyThatIsNotJson_isContractViolation_withoutQuotingTheBody() {
        ReferenceImportService service = new ReferenceImportService(List.of(), List.of(), inboundMessageService, json, events);

        assertThatThrownBy(() -> service.importMessage(message(OneCStream.MKB10, "{не json")))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage("тело сообщения не читается как JSON")
                .cause()
                .hasMessageStartingWith("tools.jackson.")
                .hasMessageNotContaining("не json");
    }

    private void assertEnvelopeRejected(String payload, String reason) {
        ReferenceImportService service = new ReferenceImportService(List.of(), List.of(), inboundMessageService, json, events);

        assertThatThrownBy(() -> service.importMessage(message(OneCStream.MKB10, payload)))
                .isInstanceOf(OneCFieldException.class)
                .hasMessage(reason);
    }

    private ReferenceImportService service() {
        return new ReferenceImportService(List.of(mkb10Mapper), List.of(priceListHandler), inboundMessageService, json, events);
    }

    private static InboundMessage message(OneCStream stream, String payload) {
        return InboundMessage.receive(new InboundReceipt(MESSAGE_ID, stream, "очередь", Instant.EPOCH), payload);
    }
}
