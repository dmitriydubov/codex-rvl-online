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
import ru.projectx.datareference.service.onec.OneCRequiredValues;
import ru.projectx.datareference.service.onec.OneCValues;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Читает из сообщения о страховой программе перечень учреждений, куда по ней можно поехать.
 *
 * <p>Строка перечня остаётся, только если её санаторий есть в справочнике и у неё ровно один тип
 * покрытия. Санаторий может отсутствовать, потому что не прошёл фильтр РВЛ или ещё не пришёл
 * из 1С. Тип покрытия неизвестен, когда источник не проставил ни рисковую, ни депозитную часть
 * или проставил обе. Причины считаются порознь: первая лечится порядком загрузки и настройкой
 * фильтра, вторая требует вопроса к 1С, и по общему счётчику выбрать между ними нельзя.
 *
 * <p>У строки, которая остаётся, обязательны номер слоя не меньше нуля, признак прямого прикрепления и процент
 * сооплаты от 0 до 100: по ним сервис Договоров выпускает договор. Нет хотя бы одного, тип не тот или значение за
 * пределом — отклоняется всё сообщение, чтобы программа не сохранилась с условиями, которых источник не задавал.
 * Строки, которые и так отбрасываются, их не проверяют.
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
     * Перечень учреждений программы и счётчики отброшенных строк.
     *
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
                OneCRequiredValues.nonNegativeInteger(row, "numberLayer"),
                OneCRequiredValues.flag(row, "directAttachment"),
                OneCRequiredValues.percent(row, "percentCopayment"),
                coverage.get()));
    }

    /**
     * Тип покрытия есть, только когда выставлен ровно один из флагов рисковой и депозитной части. Строка без флагов
     * или с обоими отбрасывается и попадает в счётчик, а не сохраняется с выбранным за источник типом.
     */
    private static Optional<ProgramLpu.CoverageType> coverageTypeOf(JsonNode row) {
        boolean risk = OneCValues.flag(row, "r");
        if (risk == OneCValues.flag(row, "d")) {
            return Optional.empty();
        }
        return Optional.of(risk ? ProgramLpu.CoverageType.R : ProgramLpu.CoverageType.D);
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
