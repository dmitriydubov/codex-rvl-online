package ru.projectx.datareference.service.policy;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import ru.projectx.datareference.entity.policy.InsuredAssociation;

public record RelationLink(UUID relatedPolicy, UUID relationType) {

    public static List<RelationLink> activeOn(Collection<InsuredAssociation.Row> rows, LocalDate day) {
        return rows.stream()
                .filter(row -> row.validOn(day))
                .map(row -> new RelationLink(row.relatedPolicy(), row.relationType()))
                .distinct()
                .toList();
    }
}
