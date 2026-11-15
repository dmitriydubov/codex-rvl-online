package ru.projectx.datareference.service.policy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ru.projectx.datareference.dto.InsuredRelativesResponse;
import ru.projectx.datareference.dto.InsuredRelativesResponse.Relative;
import ru.projectx.datareference.dto.RelativePolicy;

final class InsuredRelatives {

    private static final Comparator<Relative> ORDER = Comparator
            .comparing((Relative relative) -> relative.policy().number())
            .thenComparing(relative -> relative.relationTypeExternalId().toString())
            .thenComparing(relative -> relative.policy().externalId().toString());

    private InsuredRelatives() {
    }

    static InsuredRelativesResponse of(List<RelationLink> links, Map<UUID, RelativePolicy> policies) {
        return new InsuredRelativesResponse(links.stream()
                .filter(link -> policies.containsKey(link.relatedPolicy()))
                .map(link -> new Relative(link.relationType(), policies.get(link.relatedPolicy())))
                .sorted(ORDER)
                .toList());
    }
}
