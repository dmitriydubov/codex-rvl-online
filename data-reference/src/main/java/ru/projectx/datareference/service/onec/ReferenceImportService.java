package ru.projectx.datareference.service.onec;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.entity.InboundReceipt;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.StreamMapper;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.OneCMessageApplied;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбор одного сообщения 1С: достаёт {@code data} из сохранённого конверта и отдаёт его обработчику потока.
 * Изменения справочника и статус строки сырья фиксируются одной транзакцией, иначе сообщение применится дважды или
 * потеряется. Отказ уходит к планировщику разбора, потому что записать его можно только после отката.
 */
@Service
public class ReferenceImportService {

    private final Map<OneCStream, StreamHandler> handlers;
    private final InboundMessageService inboundMessageService;
    private final StoredEnvelope envelope;
    private final ApplicationEventPublisher events;

    /**
     * @param mappers           мапперы потоков с обычным путём: каждый становится обработчиком своего потока
     * @param dedicatedHandlers обработчики потоков с особым путём, например прайс-листов
     * @throws IllegalStateException если у одного потока оказалось два обработчика
     */
    public ReferenceImportService(
            List<StreamMapper> mappers,
            List<StreamHandler> dedicatedHandlers,
            InboundMessageService inboundMessageService,
            SupersededParentWaits supersededWaits,
            ObjectMapper json,
            ApplicationEventPublisher events) {

        this.handlers = Stream.concat(
                        mappers.stream().map(mapper ->
                                new MappedStreamHandler(mapper, inboundMessageService, supersededWaits)),
                        dedicatedHandlers.stream())
                .collect(Collectors.toUnmodifiableMap(StreamHandler::stream, Function.identity()));
        this.inboundMessageService = inboundMessageService;
        this.envelope = new StoredEnvelope(json);
        this.events = events;
    }

    /**
     * Сообщение, которое могло изменить справочник, публикует {@link OneCMessageApplied}: по нему после фиксации
     * сбрасываются кэши потока. Отказ и ожидание родителя справочник не меняют и события не публикуют.
     *
     * @throws OneCFieldException если сообщение не соответствует контракту; повторять такую ошибку бессмысленно
     */
    @Transactional
    public ImportOutcome importMessage(InboundMessage message) {
        ObjectNode data = dataOf(message);
        OneCStream stream = message.receipt().stream();
        ImportOutcome result = handlerFor(stream)
                .map(handler -> handler.handle(message, data))
                .orElseGet(() -> skipWithoutHandler(message));
        if (result.mayChangeCatalog()) {
            events.publishEvent(new OneCMessageApplied(stream));
        }
        return result;
    }

    private Optional<StreamHandler> handlerFor(OneCStream stream) {
        return Optional.ofNullable(handlers.get(stream));
    }

    /**
     * Поток без обработчика даёт пропуск, а не отказ, потому что потоки подключаются постепенно. Когда маппер
     * появится, строку разбирают заново, вернув ей статус {@code NEW}, пока её тело не стёрто по сроку хранения.
     */
    private ImportOutcome skipWithoutHandler(InboundMessage message) {
        InboundReceipt receipt = message.receipt();
        String reason = "маппера для потока " + receipt.stream() + " ещё нет";
        inboundMessageService.markSkipped(receipt.messageId(), reason);
        return ImportOutcome.skipped(reason);
    }

    /**
     * Стёртое тело — строка, у которой вышел срок хранения. Повторить её нечем: недостающее может прислать только
     * источник.
     */
    private ObjectNode dataOf(InboundMessage message) {
        return envelope.dataOf(message).orElseThrow(
                () -> new OneCFieldException("тело сообщения стёрто по сроку хранения, разбирать нечего"));
    }
}
