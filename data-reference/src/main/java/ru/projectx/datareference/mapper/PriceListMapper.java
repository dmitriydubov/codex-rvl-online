package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.value.OptionalFields.amountOrNull;
import static ru.projectx.datareference.mapper.value.OptionalFields.dateOrNull;
import static ru.projectx.datareference.mapper.value.OptionalFields.textOrNull;

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
import ru.projectx.datareference.entity.PriceListItem;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.mapper.value.OneCValues;
import ru.projectx.datareference.repository.PriceListItemRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class PriceListMapper {

    private static final Logger log = LoggerFactory.getLogger(PriceListMapper.class);

    private final PriceListItemRepository repository;
    private final CatalogReference<ContragentService> services;
    private final Clock clock;

    public PriceListMapper(
            PriceListItemRepository repository,
            CatalogReferences references,
            Clock clock) {

        this.repository = repository;
        this.services = references.services("прайс по услуге вне справочника: ");
        this.clock = clock;
    }

    public ImportResult apply(ObjectNode transfer) {
        return services.applyIfKnown(OneCValues.requiredUuid(transfer, "keyValue"),
                service -> replacePrices(service, transfer));
    }

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
