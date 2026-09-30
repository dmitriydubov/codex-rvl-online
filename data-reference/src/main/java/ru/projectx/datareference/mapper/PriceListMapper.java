package ru.projectx.datareference.mapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.PriceListItem;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.PriceListItemRepository;
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.OneCValues;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * ТЗ-9: прейскурант по одной услуге, полным срезом.
 *
 * <p><b>Не реализует {@code StreamMapper}</b>, в отличие от остальных мапперов, и это
 * не оплошность: сюда приходит не сообщение источника, а уже <i>собранная передача</i> —
 * объединение нескольких сообщений. Сборкой занимается
 * {@code service.onec.PriceListTransferAssembler}; здесь остаётся бизнес-часть: чья это
 * услуга и замещение цен.
 *
 * <p>Пустой набор строк — <b>легальный случай</b> ({@code countMessages = 0}): прайс
 * по услуге очищается. Поэтому пустота не повод для пропуска, и различать «цен нет»
 * и «передача неполная» здесь не нужно — неполную передачу сборщик сюда не отдаёт.
 */
@Component
public class PriceListMapper {

    private static final Logger log = LoggerFactory.getLogger(PriceListMapper.class);
    private static final String RECORD_SET = "recordSet";

    private final PriceListItemRepository repository;
    private final ContragentServiceRepository serviceRepository;
    private final Clock clock;

    public PriceListMapper(
            PriceListItemRepository repository,
            ContragentServiceRepository serviceRepository,
            Clock clock) {

        this.repository = repository;
        this.serviceRepository = serviceRepository;
        this.clock = clock;
    }

    /**
     * Замещает прайс по услуге целиком.
     *
     * @param transfer собранная передача: {@code keyValue} с GUID услуги и
     *                 {@code recordSet} со строками всех пакетов по возрастанию номера
     */
    public ImportResult apply(ObjectNode transfer) {
        UUID serviceExternalId = OneCValues.requiredUuid(transfer, "keyValue");
        Optional<ContragentService> service = serviceRepository.findByExternalId(serviceExternalId);
        if (service.isEmpty()) {
            return ImportResult.skipped("прайс по услуге вне справочника: " + serviceExternalId);
        }

        Instant now = clock.instant();
        int removed = repository.deleteByServiceId(service.get().id());
        List<PriceListItem> items = rowsOf(transfer).stream()
                .map(row -> PriceListItem.of(service.get(), row, now))
                .toList();
        repository.saveAll(items);

        log.debug("прайс услуги {}: снято строк {}, записано {}", serviceExternalId, removed, items.size());
        return ImportResult.applied();
    }

    private static List<PriceListItem.Row> rowsOf(ObjectNode transfer) {
        return StreamSupport.stream(transfer.path(RECORD_SET).spliterator(), false)
                .map(PriceListMapper::rowOf)
                .toList();
    }

    /**
     * У двух дат периода разные правила: начало обязательно, а конец допускает открытую
     * дату — {@code 3000-01-01} источника означает «пока не заменят» и превращается
     * в пусто общим правилом преобразования.
     */
    private static PriceListItem.Row rowOf(JsonNode row) {
        BigDecimal price = OneCValues.amount(row, "price")
                .orElseThrow(() -> new OneCFieldException("в строке прайса нет цены"));
        LocalDate validFrom = OneCValues.date(row, "period")
                .orElseThrow(() -> new OneCFieldException("в строке прайса нет даты начала действия"));

        return new PriceListItem.Row(
                OneCValues.text(row, "nameService").orElse(null),
                OneCValues.text(row, "serviceCode").orElse(null),
                OneCValues.text(row, "serviceCodeForPF").orElse(null),
                price,
                OneCValues.amount(row, "priceWithDiscount").orElse(null),
                validFrom,
                OneCValues.date(row, "periodTo").orElse(null));
    }
}
