package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.OptionalFields.dateOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.uuidOrNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.ProgramLpu;
import ru.projectx.datareference.repository.LpuRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Читает из сообщения о страховой программе перечень учреждений, куда по ней можно поехать. Строка без санатория
 * в справочнике или без однозначного типа покрытия отбрасывается, а у остальных испорченное условие обслуживания
 * отклоняет всё сообщение: по этим условиям сервис Договоров выпускает договор.
 */
@Component
public class AvailableLpuReader {

    private static final String SERVICE = "service";
    private static final String ID_LPU = "idLPU";

    private final LpuRepository lpuRepository;

    public AvailableLpuReader(LpuRepository lpuRepository) {
        this.lpuRepository = lpuRepository;
    }

    /**
     * @throws ru.projectx.datareference.exception.OneCFieldException если у остающейся строки нет обязательного условия
     *         или оно за пределом
     */
    public AvailableLpu read(ObjectNode data) {
        List<JsonNode> received = StreamSupport.stream(data.path(SERVICE).spliterator(), false).toList();
        Map<UUID, Lpu> sanatoriums = sanatoriumsOf(received);
        List<ProgramLpu.Row> rows = received.stream()
                .map(row -> rowOf(row, sanatoriums))
                .flatMap(Optional::stream)
                .toList();
        long unknownSanatorium = received.stream()
                .filter(row -> sanatoriumOf(row, sanatoriums).isEmpty())
                .count();
        return new AvailableLpu(rows, received.size(), unknownSanatorium);
    }

    /** Санатории читаются одним запросом: в перечне бывает под тысячу строк, и запрос на строку был бы дорог. */
    private Map<UUID, Lpu> sanatoriumsOf(List<JsonNode> received) {
        Set<UUID> referenced = received.stream()
                .map(row -> OneCValues.uuid(row, ID_LPU))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        return lpuRepository.findAllByExternalIdIn(referenced).stream()
                .collect(Collectors.toMap(Lpu::externalId, Function.identity()));
    }

    private static Optional<Lpu> sanatoriumOf(JsonNode row, Map<UUID, Lpu> sanatoriums) {
        return OneCValues.uuid(row, ID_LPU).map(sanatoriums::get);
    }

    private static Optional<ProgramLpu.Row> rowOf(JsonNode row, Map<UUID, Lpu> sanatoriums) {
        Optional<Lpu> sanatorium = sanatoriumOf(row, sanatoriums);
        Optional<ProgramLpu.CoverageType> coverage = coverageTypeOf(row);
        if (sanatorium.isEmpty() || coverage.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ProgramLpu.Row(
                sanatorium.get(),
                uuidOrNull(row, "idTreatment"),
                dateOrNull(row, "beginDate"),
                dateOrNull(row, "endDate"),
                OneCRequiredValues.nonNegativeInteger(row, "numberLayer"),
                OneCRequiredValues.flag(row, "directAttachment"),
                OneCRequiredValues.percent(row, "percentCopayment"),
                coverage.get()));
    }

    /**
     * Тип покрытия есть, только когда выставлен ровно один из флагов рисковой и депозитной части. Иначе строка
     * отбрасывается, а не сохраняется с типом, выбранным за источник.
     */
    private static Optional<ProgramLpu.CoverageType> coverageTypeOf(JsonNode row) {
        boolean risk = OneCValues.flag(row, "r");
        if (risk == OneCValues.flag(row, "d")) {
            return Optional.empty();
        }
        return Optional.of(risk ? ProgramLpu.CoverageType.R : ProgramLpu.CoverageType.D);
    }

    /**
     * Прочитанный перечень со счётчиками отброшенных строк. Причины считаются порознь: санаторий вне справочника
     * лечится порядком загрузки и фильтром, а строка без типа покрытия требует вопроса к 1С.
     */
    public record AvailableLpu(List<ProgramLpu.Row> rows, int received, long unknownSanatorium) {

        public AvailableLpu {
            rows = List.copyOf(rows);
        }

        /** Сколько пришедших строк не попало в перечень по любой из причин. */
        public int dropped() {
            return received - rows.size();
        }

        /** Сколько строк с известным санаторием отброшено из-за того, что у них нет типа покрытия. */
        public long withoutCoverage() {
            return dropped() - unknownSanatorium;
        }
    }
}
