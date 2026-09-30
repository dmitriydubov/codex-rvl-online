package ru.projectx.datareference.mapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.Lpu;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.Program;
import ru.projectx.datareference.entity.ProgramLpu;
import ru.projectx.datareference.repository.LpuRepository;
import ru.projectx.datareference.repository.ProgramRepository;
import ru.projectx.datareference.service.onec.ImportResult;
import ru.projectx.datareference.service.onec.OneCValues;
import ru.projectx.datareference.service.onec.StreamMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * ТЗ-6: страховые программы РВЛ и перечень доступных по ним учреждений.
 *
 * <p>Фильтр {@code rvl = true} работает так же, как фильтр РВЛ у санаториев, и по той же
 * причине различает два состояния: программы у нас нет и она не наша — просто пропуск;
 * программа есть, а в обновлении {@code rvl = false} — она помечается архивной,
 * а не исчезает.
 *
 * <p>Строки табличной части, чей санаторий не резолвится, отбрасываются — это учреждения,
 * не прошедшие фильтр РВЛ, и в перечне доступных им делать нечего. Число отброшенных
 * пишется в лог: перечень, схлопнувшийся до нуля, обязан быть отличим от программы,
 * у которой учреждений и правда нет. Причины при этом считаются порознь —
 * см. {@link #logDropped}.
 */
@Component
public class ProgramMapper implements StreamMapper {

    private static final Logger log = LoggerFactory.getLogger(ProgramMapper.class);
    private static final String SERVICE = "service";
    private static final String ID_LPU = "idLPU";

    private final ProgramRepository repository;
    private final LpuRepository lpuRepository;
    private final Clock clock;

    public ProgramMapper(ProgramRepository repository, LpuRepository lpuRepository, Clock clock) {
        this.repository = repository;
        this.lpuRepository = lpuRepository;
        this.clock = clock;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.PROGRAMS;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        UUID externalId = OneCValues.requiredUuid(data, "id");
        boolean rvl = OneCValues.flag(data, "rvl");
        Program.Details details = detailsOf(data);
        Optional<Program> existing = repository.findByExternalId(externalId);

        if (existing.isEmpty()) {
            if (!rvl) {
                return ImportResult.skipped("программа не РВЛ");
            }
            Instant now = clock.instant();
            Program program = Program.create(externalId, details, now);
            program.replaceAvailableLpu(rowsOf(data, externalId), now);
            repository.save(program);
            return ImportResult.applied();
        }

        Program program = existing.get();
        return update(program, data, details, rvl);
    }

    /**
     * Программа, потерявшая признак РВЛ, теряет и перечень учреждений: он существует
     * ровно для ответа «куда можно по этой программе», а по не-РВЛ программе нельзя
     * никуда.
     */
    private ImportResult update(Program program, ObjectNode data, Program.Details details, boolean rvl) {
        Instant now = clock.instant();
        if (rvl) {
            program.applySnapshot(details, now);
            program.replaceAvailableLpu(rowsOf(data, program.externalId()), now);
        }
        else {
            program.archiveAfterLosingRvl(details, now);
            program.replaceAvailableLpu(List.of(), now);
        }
        repository.save(program);
        return ImportResult.applied();
    }

    private List<ProgramLpu.Row> rowsOf(ObjectNode data, UUID programId) {
        List<JsonNode> incoming = StreamSupport.stream(data.path(SERVICE).spliterator(), false).toList();
        Map<UUID, Lpu> sanatoriums = sanatoriumsOf(incoming);
        List<ProgramLpu.Row> rows = incoming.stream()
                .map(row -> rowOf(row, sanatoriums))
                .flatMap(Optional::stream)
                .toList();

        logDropped(programId, incoming, sanatoriums, rows.size());
        return rows;
    }

    /**
     * Все учреждения перечня — одним запросом. Иначе это N+1 в чистом виде: в перечне
     * приходит под тысячу строк, и каждая шла бы в базу за своим санаторием.
     */
    private Map<UUID, Lpu> sanatoriumsOf(List<JsonNode> incoming) {
        Set<UUID> referenced = incoming.stream()
                .map(row -> OneCValues.uuid(row, ID_LPU))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        return lpuRepository.findAllByExternalIdIn(referenced).stream()
                .collect(Collectors.toMap(Lpu::externalId, Function.identity()));
    }

    /**
     * Причины отбрасывания считаются <b>порознь</b>, и это не украшение лога. Свести их
     * в одну строку значит сделать неразличимыми два разных отказа: «санатории программы
     * не прошли фильтр РВЛ или ещё не приехали потоком ТЗ-3» и «источник не проставил
     * ни рисковую, ни депозитную часть». Первое лечится порядком загрузки и настройкой
     * фильтра, второе — вопросом к 1С, и по общему счётчику выбрать между ними нельзя.
     *
     * <p>Счётчики не пересекаются: строка удерживается только когда сошлось и то и другое,
     * поэтому строки без типа покрытия — это остаток от строк с известным санаторием.
     */
    private void logDropped(UUID programId, List<JsonNode> incoming, Map<UUID, Lpu> sanatoriums, int kept) {
        int dropped = incoming.size() - kept;
        if (dropped == 0) {
            return;
        }
        long unknownSanatorium = incoming.stream()
                .filter(row -> sanatoriumOf(row, sanatoriums).isEmpty())
                .count();
        log.info("программа {}: {} из {} строк перечня отброшено — {} с санаторием вне справочника, "
                        + "{} без типа покрытия",
                programId, dropped, incoming.size(), unknownSanatorium, dropped - unknownSanatorium);
    }

    private static Optional<Lpu> sanatoriumOf(JsonNode row, Map<UUID, Lpu> sanatoriums) {
        return OneCValues.uuid(row, ID_LPU).map(sanatoriums::get);
    }

    private Optional<ProgramLpu.Row> rowOf(JsonNode row, Map<UUID, Lpu> sanatoriums) {
        Optional<Lpu> sanatorium = sanatoriumOf(row, sanatoriums);
        Optional<ProgramLpu.CoverageType> coverage = coverageTypeOf(row);
        if (sanatorium.isEmpty() || coverage.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ProgramLpu.Row(
                sanatorium.get(),
                OneCValues.uuid(row, "idTreatment").orElse(null),
                OneCValues.date(row, "beginDate").orElse(null),
                OneCValues.date(row, "endDate").orElse(null),
                OneCValues.integer(row, "numberLayer").orElse(0),
                OneCValues.flag(row, "directAttachment"),
                OneCValues.amount(row, "percentCopayment").orElse(BigDecimal.ZERO),
                coverage.get()));
    }

    /**
     * Флаги рисковой и депозитной части взаимоисключающие, но источник не гарантирует,
     * что ровно один из них выставлен. Строка без типа покрытия не имеет смысла — она
     * отбрасывается и попадает в счётчик, а не сохраняется с придуманным значением.
     */
    private static Optional<ProgramLpu.CoverageType> coverageTypeOf(JsonNode row) {
        if (OneCValues.flag(row, "r")) {
            return Optional.of(ProgramLpu.CoverageType.R);
        }
        return OneCValues.flag(row, "d") ? Optional.of(ProgramLpu.CoverageType.D) : Optional.empty();
    }

    private static Program.Details detailsOf(ObjectNode data) {
        return new Program.Details(
                OneCValues.uuid(data, "idInsProduct").orElse(null),
                OneCValues.uuid(data, "idParent").orElse(null),
                OneCValues.requiredText(data, "name"),
                OneCValues.text(data, "typeProgramm").orElse(null),
                OneCValues.flag(data, "deleteMark"));
    }
}
