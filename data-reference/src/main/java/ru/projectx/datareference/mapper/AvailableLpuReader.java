package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.OptionalFields.dateOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.uuidOrNull;

import java.math.BigDecimal;
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
import ru.projectx.datareference.service.onec.OneCValues;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Читает из сообщения о страховой программе перечень учреждений, куда по ней можно поехать.
 *
 * <p>Строка перечня остаётся, только если её санаторий есть в справочнике и у неё указан тип
 * покрытия. Санаторий может отсутствовать, потому что не прошёл фильтр РВЛ или ещё не пришёл
 * из 1С. Тип покрытия может отсутствовать, потому что источник не проставил ни рисковую, ни
 * депозитную часть. Обе причины считаются порознь: первая лечится порядком загрузки и настройкой
 * фильтра, вторая требует вопроса к 1С, и по общему счётчику выбрать между ними нельзя.
 */
@Component
public class AvailableLpuReader {

    private static final String SERVICE = "service";
    private static final String ID_LPU = "idLPU";

    private final LpuRepository lpuRepository;

    public AvailableLpuReader(LpuRepository lpuRepository) {
        this.lpuRepository = lpuRepository;
    }

    /** Перечень учреждений программы и счётчики отброшенных строк. */
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

    /**
     * Все санатории перечня читаются одним запросом. Иначе в перечне приходит под тысячу строк,
     * и каждая шла бы в базу за своим санаторием.
     */
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
                OneCValues.integer(row, "numberLayer").orElse(0),
                OneCValues.flag(row, "directAttachment"),
                OneCValues.amount(row, "percentCopayment").orElse(BigDecimal.ZERO),
                coverage.get()));
    }

    /**
     * Рисковая и депозитная части взаимоисключающие, но источник не обещает, что выставлен ровно
     * один флаг. Строка без типа покрытия смысла не имеет: она отбрасывается и попадает в счётчик,
     * а не сохраняется с придуманным значением.
     */
    private static Optional<ProgramLpu.CoverageType> coverageTypeOf(JsonNode row) {
        if (OneCValues.flag(row, "r")) {
            return Optional.of(ProgramLpu.CoverageType.R);
        }
        return OneCValues.flag(row, "d") ? Optional.of(ProgramLpu.CoverageType.D) : Optional.empty();
    }

    /**
     * Прочитанный перечень: строки, которые остались, сколько строк пришло и сколько из них
     * отброшено из-за санатория вне справочника.
     *
     * <p>Счётчики не пересекаются. Строка остаётся, только когда известны и санаторий, и тип
     * покрытия, поэтому строки без типа покрытия — это остаток отброшенных строк с известным
     * санаторием.
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
