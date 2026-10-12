package ru.projectx.datareference.mapper;

import static ru.projectx.datareference.mapper.OptionalFields.textOrNull;
import static ru.projectx.datareference.mapper.OptionalFields.uuidOrNull;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.OneCStream;
import ru.projectx.datareference.entity.Program;
import ru.projectx.datareference.entity.ProgramLpu;
import ru.projectx.datareference.repository.ProgramRepository;
import tools.jackson.databind.node.ObjectNode;

@Component
public class ProgramMapper implements StreamMapper {

    private static final Logger log = LoggerFactory.getLogger(ProgramMapper.class);
    private static final String NOT_RVL = "программа не РВЛ";

    private final ProgramRepository repository;
    private final AvailableLpuReader availableLpuReader;
    private final SnapshotUpsert<Program, RvlProgramSnapshot> upsert;
    private final Clock clock;

    public ProgramMapper(ProgramRepository repository, AvailableLpuReader availableLpuReader, Clock clock) {
        this.repository = repository;
        this.availableLpuReader = availableLpuReader;
        this.upsert = new SnapshotUpsert<>(
                repository::findByExternalId, ProgramMapper::create, ProgramMapper::refresh, repository::save);
        this.clock = clock;
    }

    @Override
    public OneCStream stream() {
        return OneCStream.PROGRAMS;
    }

    @Override
    public ImportResult apply(ObjectNode data) {
        UUID externalId = OneCValues.requiredUuid(data, "id");
        Program.Details details = detailsOf(data);
        return OneCValues.flag(data, "rvl")
                ? applyRvl(externalId, details, data)
                : applyWithoutRvl(externalId, details);
    }

    private ImportResult applyRvl(UUID externalId, Program.Details details, ObjectNode data) {
        AvailableLpuReader.AvailableLpu availableLpu = availableLpuReader.read(data);
        logDropped(externalId, availableLpu);
        return upsert.apply(externalId, new RvlProgramSnapshot(details, availableLpu.rows()), clock.instant());
    }

    private ImportResult applyWithoutRvl(UUID externalId, Program.Details details) {
        return repository.findByExternalId(externalId)
                .map(program -> archiveAfterLosingRvl(program, details))
                .orElseGet(() -> ImportResult.skipped(NOT_RVL));
    }

    private ImportResult archiveAfterLosingRvl(Program program, Program.Details details) {
        Instant now = clock.instant();
        program.archiveAfterLosingRvl(details, now);
        program.replaceAvailableLpu(List.of(), now);
        repository.save(program);
        return ImportResult.applied();
    }

    private static Program create(UUID externalId, RvlProgramSnapshot snapshot, Instant now) {
        Program program = Program.create(externalId, snapshot.details(), now);
        program.replaceAvailableLpu(snapshot.availableLpu(), now);
        return program;
    }

    private static void refresh(Program program, RvlProgramSnapshot snapshot, Instant now) {
        program.applySnapshot(snapshot.details(), now);
        program.replaceAvailableLpu(snapshot.availableLpu(), now);
    }

    private static void logDropped(UUID programId, AvailableLpuReader.AvailableLpu availableLpu) {
        if (availableLpu.dropped() == 0) {
            return;
        }
        log.info("программа {}: {} из {} строк перечня отброшено — {} с санаторием вне справочника, "
                        + "{} без типа покрытия",
                programId, availableLpu.dropped(), availableLpu.received(), availableLpu.unknownSanatorium(),
                availableLpu.withoutCoverage());
    }

    private static Program.Details detailsOf(ObjectNode data) {
        return new Program.Details(
                uuidOrNull(data, "idInsProduct"),
                uuidOrNull(data, "idParent"),
                OneCValues.requiredText(data, "name"),
                textOrNull(data, "typeProgramm"),
                OneCValues.flag(data, "deleteMark"));
    }

    private record RvlProgramSnapshot(Program.Details details, List<ProgramLpu.Row> availableLpu) {
    }
}
