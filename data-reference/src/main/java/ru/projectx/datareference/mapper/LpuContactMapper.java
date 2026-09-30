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
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.OneCValues;
import ru.projectx.datareference.service.onec.StreamMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * ТЗ-8: адрес и регион санатория приходят отдельным потоком контактной информации —
 * тем же, которым едут телефоны и почта всех контрагентов 1С:УСК. Отсюда два фильтра:
 * сообщение должно относиться к нашему санаторию, а внутри него нужны только
 * строки-адреса.
 *
 * <p><b>Режим замещения обязателен:</b> если подходящих строк в сообщении нет, адрес
 * и регион очищаются. Оставить прежние значения означало бы показывать в каталоге адрес,
 * которого в 1С уже нет, — и никогда об этом не узнать.
 *
 * <p>Как и во всех потоках, применяется последнее полученное сообщение.
 */
@Component
public class LpuContactMapper implements StreamMapper {

    private static final String RECORD_SET = "recordSet";
    private static final String FILTER = "filter";
    private static final String FIELD_NAME = "fieldName";
    private static final String OBJECT = "object";
    private static final String VALUE = "value";
    private static final String ROW_NUMBER = "rowNumber";

    private final LpuRepository repository;
    private final OneCContractProperties contract;
    private final Clock clock;

    public LpuContactMapper(LpuRepository repository, OneCContractProperties contract, Clock clock) {
        this.repository = repository;
        this.contract = contract;
        this.clock = clock;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.LPU_CONTACTS;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        UUID contragentId = contragentOf(data);
        Optional<Lpu> sanatorium = repository.findByExternalId(contragentId);
        if (sanatorium.isEmpty()) {
            return ImportResult.skipped("контакты не нашего контрагента: " + contragentId);
        }

        Optional<JsonNode> address = selectAddress(data);
        sanatorium.get().applyContacts(
                address.flatMap(row -> OneCValues.text(row, "representation")).orElse(null),
                address.flatMap(row -> OneCValues.text(row, "field2")).orElse(null),
                clock.instant());
        repository.save(sanatorium.get());
        return ImportResult.applied();
    }

    /**
     * Владелец контактов лежит в фильтре сообщения; в строках набора тот же GUID
     * продублирован. Читаем фильтр, а строку — запасным вариантом: набор может оказаться
     * пустым, и тогда очищать адрес всё равно надо у конкретного санатория.
     */
    private static UUID contragentOf(ObjectNode data) {
        return filterValue(data)
                .or(() -> firstRecordOwner(data))
                .orElseThrow(() -> new OneCFieldException("в сообщении ТЗ-8 не найден GUID контрагента"));
    }

    private static Optional<UUID> filterValue(JsonNode data) {
        return rows(data, FILTER)
                .filter(row -> OBJECT.equals(row.path(FIELD_NAME).asString(null)))
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
     * Приоритет видов адреса задан конфигурацией; строка, чей вид не назван вовсе,
     * участвует последней — «хоть какой-то адрес» лучше пустой карточки, но проигрывает
     * названному.
     */
    private Optional<JsonNode> selectAddress(ObjectNode data) {
        List<String> priority = contract.addressKindPriority();
        return rows(data, RECORD_SET)
                .filter(this::isValidAddressRow)
                .min(Comparator
                        .comparingInt((JsonNode row) -> rankOf(row, priority))
                        .thenComparingInt(row -> row.path(ROW_NUMBER).asInt(Integer.MAX_VALUE)));
    }

    /** Недействительный адрес — это не адрес: источник помечает такие строки сам. */
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
