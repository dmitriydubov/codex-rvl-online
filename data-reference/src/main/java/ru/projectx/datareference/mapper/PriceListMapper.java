package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.OptionalFields.amountOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.dateOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.textOrNull;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.ContragentService;
import ru.projectx.datareference.entity.ParentKind;
import ru.projectx.datareference.entity.PriceListItem;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.ContragentServiceRepository;
import ru.projectx.datareference.repository.PriceListItemRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Замещает прейскурант санатория по одной услуге полным срезом из 1С:УСК.
 *
 * <p>В отличие от остальных мапперов, этот не разбирает отдельное сообщение источника. 1С присылает
 * прейскурант по услуге несколькими сообщениями, и сюда приходит уже собранная передача: строки всех
 * её сообщений вместе. Собирает передачу сборщик прайс-листов в разборе сообщений, а здесь
 * остаются правила справочника: принимаются только цены услуг, которые есть в справочнике, и старые
 * цены услуги целиком заменяются новыми.
 *
 * <p>Пустой набор строк — штатный случай: 1С так сообщает, что цен по услуге больше нет, и прейскурант
 * услуги очищается. Неполную передачу сборщик сюда не отдаёт, поэтому пустота здесь всегда означает
 * «цен нет».
 */
@Component
public class PriceListMapper {

    private static final Logger log = LoggerFactory.getLogger(PriceListMapper.class);

    private final PriceListItemRepository repository;
    private final CatalogReference<ContragentService> services;
    private final Clock clock;

    public PriceListMapper(
            PriceListItemRepository repository,
            ContragentServiceRepository serviceRepository,
            ForeignReferences foreign,
            Clock clock) {

        this.repository = repository;
        this.services = new CatalogReference<>(serviceRepository::findByExternalId, ParentKind.CONTRAGENT_SERVICE,
                foreign, "прайс по услуге вне справочника: ");
        this.clock = clock;
    }

    /**
     * Замещает прейскурант услуги целиком. Услуга, признанная не нашей, даёт пропуск с GUID услуги в причине, а
     * услуга, которой ещё нет, — ожидание её; прежние цены в обоих случаях не трогаются. Строки разбираются до снятия
     * прежних цен: битая строка их не задевает.
     *
     * @param transfer собранная передача: {@code keyValue} с GUID услуги и {@code recordSet} со строками
     *                 всех её сообщений по возрастанию номера строки
     * @throws OneCFieldException если в передаче нет GUID услуги или строка без цены или даты начала
     */
    public ImportResult apply(ObjectNode transfer) {
        return services.applyIfKnown(OneCValues.requiredUuid(transfer, "keyValue"),
                service -> replacePrices(service, transfer));
    }

    /**
     * Проверяет строку прайса по правилам замещения, ничего не записывая: так строку можно отклонить, когда пришёл
     * её пакет, а не когда соберётся вся передача.
     *
     * @throws OneCFieldException если в строке нет цены или даты начала
     */
    public void checkRow(JsonNode row) {
        rowOf(row);
    }

    private ImportResult replacePrices(ContragentService service, ObjectNode transfer) {
        Instant now = clock.instant();
        List<PriceListItem> items = rowsOf(transfer).stream()
                .map(row -> PriceListItem.of(service, row, now))
                .toList();
        int removed = repository.deleteByServiceId(service.id());
        repository.saveAll(items);

        log.debug("прайс услуги {}: снято строк {}, записано {}", service.externalId(), removed, items.size());
        return ImportResult.applied();
    }

    private static List<PriceListItem.Row> rowsOf(ObjectNode transfer) {
        return StreamSupport.stream(transfer.path("recordSet").spliterator(), false)
                .map(PriceListMapper::rowOf)
                .toList();
    }

    /**
     * У двух дат периода разные правила. Начало обязательно, а конец может быть открытым: дата
     * {@code 3000-01-01} у 1С значит «пока цену не заменят», и общее правило чтения дат делает её пустой.
     * Цена и дата начала читаются первыми, чтобы отказ называл их, а не другое поле.
     */
    private static PriceListItem.Row rowOf(JsonNode row) {
        BigDecimal price = priceOf(row);
        LocalDate validFrom = validFromOf(row);
        return new PriceListItem.Row(
                textOrNull(row, "nameService"),
                textOrNull(row, "serviceCode"),
                textOrNull(row, "serviceCodeForPF"),
                price,
                amountOrNull(row, "priceWithDiscount"),
                validFrom,
                dateOrNull(row, "periodTo"));
    }

    private static BigDecimal priceOf(JsonNode row) {
        return OneCValues.amount(row, "price")
                .orElseThrow(() -> new OneCFieldException("в строке прайса нет цены"));
    }

    private static LocalDate validFromOf(JsonNode row) {
        return OneCValues.date(row, "period")
                .orElseThrow(() -> new OneCFieldException("в строке прайса нет даты начала действия"));
    }
}
