package ru.projectx.datareference.service.policy;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import ru.projectx.datareference.dto.InsuredRelativesResponse;
import ru.projectx.datareference.entity.policy.InsuredAssociation;
import ru.projectx.datareference.entity.policy.Policy;
import ru.projectx.datareference.repository.policy.InsuredAssociationRepository;
import ru.projectx.datareference.service.rest.ReferenceCalendar;
import ru.projectx.datareference.service.rest.RestReadTransaction;

@Service
public class InsuredRelativesService implements InsuredRelativesReads {

    private final PolicyLookup lookup;
    private final InsuredAssociationRepository associations;
    private final RelativePolicies relativePolicies;
    private final ReferenceCalendar calendar;

    public InsuredRelativesService(PolicyLookup lookup, InsuredAssociationRepository associations,
            RelativePolicies relativePolicies, ReferenceCalendar calendar) {

        this.lookup = lookup;
        this.associations = associations;
        this.relativePolicies = relativePolicies;
        this.calendar = calendar;
    }

    @Override
    @RestReadTransaction
    public InsuredRelativesResponse ofPolicy(UUID externalId) {
        return relativesOf(lookup.byExternalId(externalId));
    }

    @Override
    @RestReadTransaction
    public InsuredRelativesResponse ofPolicyNumber(String number, UUID contractExternalId) {
        return relativesOf(lookup.byNumber(number, contractExternalId));
    }

    private InsuredRelativesResponse relativesOf(Policy insured) {
        List<InsuredAssociation.Row> rows = associations.findAllByInsuredPolicyExternalId(insured.externalId()).stream()
                .map(InsuredAssociation::row)
                .toList();
        List<RelationLink> links = RelationLink.activeOn(rows, calendar.today());
        return InsuredRelatives.of(links,
                relativePolicies.byExternalIds(links.stream().map(RelationLink::relatedPolicy).distinct().toList()));
    }
}
