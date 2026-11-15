package ru.projectx.datareference.service.policy;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import ru.projectx.datareference.entity.catalog.Program;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.entity.policy.PolicyProgram;
import ru.projectx.datareference.entity.policy.PolicyServiceLine;
import ru.projectx.datareference.repository.catalog.ProgramRepository;

@Component
public class RvlProgramCatalog {

    private final ProgramRepository programs;

    public RvlProgramCatalog(ProgramRepository programs) {
        this.programs = programs;
    }

    public Map<UUID, Program> referencedBy(Collection<Policy> policies) {
        Set<UUID> referenced = policies.stream()
                .flatMap(RvlProgramCatalog::programIds)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (referenced.isEmpty()) {
            return Map.of();
        }
        return programs.findAllByExternalIdIn(referenced).stream()
                .filter(program -> !program.isArchive())
                .collect(Collectors.toMap(Program::externalId, Function.identity()));
    }

    private static Stream<UUID> programIds(Policy policy) {
        return Stream.concat(
                policy.programs().stream().map(PolicyProgram::externalProgramId),
                policy.serviceLines().stream().map(PolicyServiceLine::externalProgramId));
    }
}
