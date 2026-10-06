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
import ru.projectx.datareference.mapper.ImportResult;
import ru.projectx.datareference.mapper.StreamMapper;
import ru.projectx.datareference.service.InboundMessageService;
import ru.projectx.datareference.service.OneCMessageApplied;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбор одного сообщения 1С: превращение сохранённого сырья в строки справочника.
 *
 * <p>Путь сообщения читается сверху вниз. Из конверта {@code {data, metadata}} достаётся {@code data}, затем
 * по потоку сообщения выбирается его обработчик. Обработчик применяет правила своего потока и записывает исход
 * в строку сырья. У большинства потоков обработчик — маппер потока с записью исхода. Поток с особым путём, как
 * прайс-листы, которые приходят частями, приносит свой обработчик отдельным компонентом, и этот класс при этом
 * не меняется.
 *
 * <p><b>Транзакция одна на сообщение, и это существенно.</b> Изменения справочника и новый статус строки сырья
 * фиксируются вместе. Если их разъединить, сообщение либо применится дважды, когда статус не записался, либо
 * потеряется, когда статус записался, а изменения откатились.
 *
 * <p>Разобранное сообщение, применённое или пропущенное, публикует событие {@link OneCMessageApplied}: по нему
 * после фиксации транзакции сбрасываются кэши ответов, которые зависят от потока сообщения. Отказ события не
 * публикует: транзакция откатывается, и справочник не меняется.
 *
 * <p>Когда повторять и что делать с отказом, решает не этот класс. Исключение уходит к планировщику разбора,
 * потому что отказ записывается уже после отката этой транзакции.
 */
@Service
public class ReferenceImportService {

    private final Map<OneCStream, StreamHandler> handlers;
    private final InboundMessageService inboundMessageService;
    private final StoredEnvelope envelope;
    private final ApplicationEventPublisher events;

    /**
     * @param mappers           мапперы потоков с обычным путём: каждый становится обработчиком своего потока
     * @param dedicatedHandlers обработчики потоков с особым путём
     * @param events            публикация события о разобранном сообщении
     * @throws IllegalStateException если у одного потока оказалось два обработчика
     */
    public ReferenceImportService(
            List<StreamMapper> mappers,
            List<StreamHandler> dedicatedHandlers,
            InboundMessageService inboundMessageService,
            ObjectMapper json,
            ApplicationEventPublisher events) {

        this.handlers = Stream.concat(
                        mappers.stream().map(mapper -> new MappedStreamHandler(mapper, inboundMessageService)),
                        dedicatedHandlers.stream())
                .collect(Collectors.toUnmodifiableMap(StreamHandler::stream, Function.identity()));
        this.inboundMessageService = inboundMessageService;
        this.envelope = new StoredEnvelope(json);
        this.events = events;
    }

    /**
     * Разбирает сообщение и записывает исход в его строку сырья.
     *
     * @throws OneCFieldException если сообщение не соответствует контракту; это постоянная ошибка, повторять
     *         её бессмысленно
     */
    @Transactional
    public ImportResult importMessage(InboundMessage message) {
        ObjectNode data = dataOf(message);
        OneCStream stream = message.receipt().stream();
        ImportResult result = handlerFor(stream)
                .map(handler -> handler.handle(message, data))
                .orElseGet(() -> skipWithoutHandler(message));
        events.publishEvent(new OneCMessageApplied(stream));
        return result;
    }

    private Optional<StreamHandler> handlerFor(OneCStream stream) {
        return Optional.ofNullable(handlers.get(stream));
    }

    /**
     * Поток без обработчика — пропуск, а не отказ. Потоки подключаются постепенно, и сообщение потока, до
     * которого ещё не дошли руки, ждёт своего маппера в разобранном виде, а не в виде ошибки. Когда маппер
     * появится, строку разбирают заново, вернув ей статус {@code NEW}, пока её тело не стёрто по сроку хранения.
     */
    private ImportResult skipWithoutHandler(InboundMessage message) {
        InboundReceipt receipt = message.receipt();
        String reason = "маппера для потока " + receipt.stream() + " ещё нет";
        inboundMessageService.markSkipped(receipt.messageId(), reason);
        return ImportResult.skipped(reason);
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
