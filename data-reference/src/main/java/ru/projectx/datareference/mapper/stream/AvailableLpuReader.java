package ru.projectx.datareference.mapper.stream;

import static ru.projectx.datareference.mapper.value.OptionalFields.dateOrNull;
import static ru.projectx.datareference.mapper.value.OptionalFields.uuidOrNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.catalog.Lpu;
import ru.projectx.datareference.entity.catalog.ProgramLpu;
import ru.projectx.datareference.mapper.value.OneCRequiredValues;
import ru.projectx.datareference.mapper.value.OneCValues;
import ru.projectx.datareference.repository.catalog.LpuRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class AvailableLpuReader {

    private static final String SERVICE = "service";
    private static final String ID_LPU = "idLPU";

    private final LpuRepository lpuRepository;

    public AvailableLpuReader(LpuRepository lpuRepository) {
        this.lpuRepository = lpuRepository;
    }

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

    private static Optional<ProgramLpu.CoverageType> coverageTypeOf(JsonNode row) {
        boolean risk = OneCValues.flag(row, "r");
        if (risk == OneCValues.flag(row, "d")) {
            return Optional.empty();
        }
        return Optional.of(risk ? ProgramLpu.CoverageType.R : ProgramLpu.CoverageType.D);
    }

    public record AvailableLpu(List<ProgramLpu.Row> rows, int received, long unknownSanatorium) {

        public AvailableLpu {
            rows = List.copyOf(rows);
        }

        public int dropped() {
            return received - rows.size();
        }

        public long withoutCoverage() {
            return dropped() - unknownSanatorium;
        }
    }
}
