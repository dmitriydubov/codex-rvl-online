package ru.projectx.datareference.service.onec;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.InboundMessage;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.service.InboundMessageService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбор одного сообщения: превращение сырья из 1С в строки справочника.
 *
 * <p>Весь путь одного сообщения, сверху вниз:
 * <ol>
 *   <li>по потоку сообщения выбирается маппер — свой на каждое ТЗ;</li>
 *   <li>из конверта {@code {data, metadata}} достаётся {@code data};</li>
 *   <li>маппер применяет правила своего потока: фильтры, контроль версии, upsert;</li>
 *   <li>исход записывается в ту же строку сырья.</li>
 * </ol>
 *
 * <p><b>Транзакция одна на сообщение, и это существенно.</b> Изменения справочника
 * и новый статус строки сырья коммитятся вместе. Разъединить их значит получить либо
 * сообщение, применённое дважды (статус не записался), либо потерянное (статус
 * записался, изменения откатились).
 *
 * <p>Когда повторять и что делать с отказом — не здесь: исключение улетает наверх,
 * к шедулеру, потому что записывать отказ нужно уже <i>после</i> отката этой транзакции.
 */
@Service
public class ReferenceImportService {

    private static final String DATA_FIELD = "data";

    private final Map<OneCStream, StreamMapper> mappers;
    private final PriceListTransferAssembler priceListAssembler;
    private final InboundMessageService inboundMessageService;
    private final ObjectMapper json;

    public ReferenceImportService(
            List<StreamMapper> mappers,
            PriceListTransferAssembler priceListAssembler,
            InboundMessageService inboundMessageService,
            ObjectMapper json) {

        this.mappers = mappers.stream()
                .collect(Collectors.toUnmodifiableMap(StreamMapper::stream, Function.identity()));
        this.priceListAssembler = priceListAssembler;
        this.inboundMessageService = inboundMessageService;
        this.json = json;
    }

    /**
     * @throws OneCFieldException если сообщение не соответствует контракту — ошибка
     *         постоянная, повторять её бессмысленно
     */
    @Transactional
    public ImportResult importMessage(InboundMessage message) {
        ObjectNode data = dataOf(message);

        // Прайсы (ТЗ-9) приходят частями, и применить одно сообщение нельзя. У них свой
        // путь: сборщик копит части и ведёт статусы сразу всей передачи, а не этой одной
        // строки, — поэтому исход записывает он сам, и общая ветка ниже сюда не годится.
        if (message.stream() == OneCStream.PRICE_LISTS) {
            return priceListAssembler.receive(message, data);
        }

        ImportResult result = mapperFor(message.stream())
                .map(mapper -> mapper.apply(data))
                .orElseGet(() -> ImportResult.skipped("маппера для потока " + message.stream() + " ещё нет"));
        recordOutcome(message, result);
        return result;
    }

    private void recordOutcome(InboundMessage message, ImportResult result) {
        switch (result) {
            case ImportResult.Applied ignored -> inboundMessageService.markProcessed(message.messageId());
            case ImportResult.Skipped skipped -> inboundMessageService.markSkipped(message.messageId(), skipped.reason());
        }
    }

    /**
     * Отсутствующий маппер — это пропуск, а не отказ. Потоки подключаются постепенно,
     * и сообщение потока, до которого ещё не дошли руки, обязано дождаться своего
     * маппера в разобранном виде, а не в виде ошибки. Разбирается оно переводом строки
     * обратно в {@code NEW}, когда маппер появится.
     */
    private Optional<StreamMapper> mapperFor(OneCStream stream) {
        return Optional.ofNullable(mappers.get(stream));
    }

    /**
     * Полезная нагрузка лежит в {@code data} конверта; {@code metadata} нужна приёму,
     * а не разбору — на этом шаге из неё уже всё взято.
     */
    private ObjectNode dataOf(InboundMessage message) {
        JsonNode data = readTree(message).get(DATA_FIELD);
        if (data == null || !data.isObject()) {
            throw new OneCFieldException("в конверте сообщения нет объекта data");
        }
        return (ObjectNode) data;
    }

    private JsonNode readTree(InboundMessage message) {
        String payload = message.payload();
        if (payload == null) {
            // Так выглядит строка полиса, у которой вышел срок хранения тела. Повторить
            // её нечем: недостающее может прислать только источник.
            throw new OneCFieldException("тело сообщения стёрто по сроку хранения, разбирать нечего");
        }
        try {
            return json.readTree(payload);
        }
        catch (JacksonException malformed) {
            // До таблицы доходит только то, что PostgreSQL принял в jsonb, так что
            // случай почти невозможный — но «почти» здесь не повод для тихого NPE.
            throw new OneCFieldException("тело сообщения не читается как JSON", malformed);
        }
    }
}
