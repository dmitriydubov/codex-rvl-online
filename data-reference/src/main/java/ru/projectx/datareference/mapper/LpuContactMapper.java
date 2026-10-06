package ru.projectx.datareference.mapper;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.exception.OneCFieldException;
import ru.projectx.datareference.repository.LpuRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Разбирает адрес и регион санатория из потока контактной информации 1С:УСК. Тем же потоком
 * идут телефоны и почта всех контрагентов 1С, поэтому фильтров два: сообщение должно относиться
 * к санаторию из справочника, а из его строк нужны только действительные адреса. Контакты контрагента,
 * признанного не нашим, пропускаются, а контакты контрагента, который ещё не пришёл, ждут его.
 *
 * <p>Адрес замещается целиком. Если подходящих строк в сообщении нет, адрес и регион очищаются:
 * прежние значения показывали бы в каталоге адрес, которого в 1С уже нет, и узнать об этом было бы
 * неоткуда.
 *
 * <p>Версий у сообщений нет, поэтому действует последнее полученное.
 */
@Component
public class LpuContactMapper implements StreamMapper {

    private static final String RECORD_SET = "recordSet";
    private static final String OBJECT = "object";
    private static final String VALUE = "value";

    private final LpuRepository repository;
    private final CatalogReference<Lpu> sanatoriums;
    private final OneCContractProperties contract;
    private final Clock clock;

    public LpuContactMapper(LpuRepository repository, CatalogReferences references, OneCContractProperties contract,
                            Clock clock) {
        this.repository = repository;
        this.sanatoriums = references.sanatoriums("контакты не нашего контрагента: ");
        this.contract = contract;
        this.clock = clock;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.LPU_CONTACTS;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        return sanatoriums.applyIfKnown(contragentOf(data), sanatorium -> replaceAddress(sanatorium, data));
    }

    /** Запись, которую меняет сообщение, — сам контрагент: адрес лежит в его строке справочника. */
    @Override
    public Optional<UUID> recordOf(ObjectNode data) {
        return Optional.of(contragentOf(data));
    }

    private ImportResult replaceAddress(Lpu sanatorium, ObjectNode data) {
        Optional<JsonNode> address = selectAddress(data);
        sanatorium.applyContacts(
                address.flatMap(row -> OneCValues.text(row, "representation")).orElse(null),
                address.flatMap(row -> OneCValues.text(row, "field2")).orElse(null),
                clock.instant());
        repository.save(sanatorium);
        return ImportResult.applied();
    }

    /**
     * GUID контрагента, чьи это контакты, лежит в фильтре сообщения, и в каждой строке набора он
     * повторяется. Сначала читается фильтр, а первая строка набора служит запасным вариантом. Набор строк
     * может быть пустым, и тогда адрес всё равно нужно очистить у конкретного санатория.
     */
    private static UUID contragentOf(ObjectNode data) {
        return filterValue(data)
                .or(() -> firstRecordOwner(data))
                .orElseThrow(() -> new OneCFieldException("в сообщении контактной информации не найден GUID контрагента"));
    }

    private static Optional<UUID> filterValue(JsonNode data) {
        return rows(data, "filter")
                .filter(row -> OBJECT.equals(row.path("fieldName").asString(null)))
                .map(row -> OneCValues.uuid(row.path(VALUE), VALUE))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<UUID> firstRecordOwner(JsonNode data) {
        return rows(data, RECORD_SET)
                .map(row -> OneCValues.uuid(row.path(OBJECT), VALUE))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * Порядок предпочтения видов адреса задан настройкой. Адрес вида, которого в списке нет, тоже
     * подходит, но проигрывает любому названному: какой-то адрес лучше пустой карточки санатория. Среди
     * равных выигрывает строка с меньшим номером.
     */
    private Optional<JsonNode> selectAddress(ObjectNode data) {
        List<String> priority = contract.addressKindPriority();
        return rows(data, RECORD_SET)
                .filter(this::isValidAddressRow)
                .min(Comparator
                        .comparingInt((JsonNode row) -> rankOf(row, priority))
                        .thenComparingInt(row -> row.path("rowNumber").asInt(Integer.MAX_VALUE)));
    }

    /** Строка-адрес, которую 1С не пометила недействительной. */
    private boolean isValidAddressRow(JsonNode row) {
        return contract.addressRecordType().equals(row.path("type").asString(null))
                && !OneCValues.flag(row, "notValid");
    }

    private static int rankOf(JsonNode row, List<String> priority) {
        String kind = row.path("kind").path("representation").asString("");
        int index = priority.indexOf(kind);
        return index < 0 ? priority.size() : index;
    }

    private static Stream<JsonNode> rows(JsonNode data, String field) {
        return StreamSupport.stream(data.path(field).spliterator(), false);
    }
}
