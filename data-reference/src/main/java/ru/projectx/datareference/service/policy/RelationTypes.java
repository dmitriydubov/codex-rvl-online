package ru.projectx.datareference.service.policy;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import ru.projectx.datareference.entity.policy.InsuredAssociation;

public record RelationTypes(Map<UUID, Set<UUID>> byRelatedPolicy) {

    public static RelationTypes activeOn(Collection<InsuredAssociation.Row> rows, LocalDate day) {
        return new RelationTypes(rows.stream()
                .filter(row -> row.validOn(day))
                .collect(Collectors.groupingBy(InsuredAssociation.Row::relatedPolicy,
                        Collectors.mapping(InsuredAssociation.Row::relationType, Collectors.toSet()))));
    }

    public Set<UUID> relatedPolicies() {
        return byRelatedPolicy.keySet();
    }

    public @Nullable UUID soleTypeOf(UUID relatedPolicy) {
        Set<UUID> types = byRelatedPolicy.getOrDefault(relatedPolicy, Set.of());
        return types.size() == 1 ? types.iterator().next() : null;
    }
}
