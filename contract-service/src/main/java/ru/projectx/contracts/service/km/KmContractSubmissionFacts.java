package ru.projectx.contracts.service.km;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.ContractAcceptance;
import ru.projectx.contracts.entity.ContractPerson;
import ru.projectx.contracts.entity.PersonRole;
import ru.projectx.contracts.entity.PersonSnapshot;
import ru.projectx.contracts.entity.PrintedForm;
import ru.projectx.contracts.repository.ContractAcceptanceRepository;
import ru.projectx.contracts.repository.ContractPersonRepository;
import ru.projectx.contracts.repository.PrintedFormRepository;
import ru.projectx.contracts.service.outbox.OutboundAttachment;
import ru.projectx.contracts.service.reference.ExternalIdentifiers;

@Component
public class KmContractSubmissionFacts {

    private final ContractPersonRepository persons;
    private final ContractAcceptanceRepository acceptances;
    private final PrintedFormRepository forms;

    public KmContractSubmissionFacts(ContractPersonRepository persons, ContractAcceptanceRepository acceptances,
            PrintedFormRepository forms) {
        this.persons = persons;
        this.acceptances = acceptances;
        this.forms = forms;
    }

    public SubmissionFacts gather(Contract contract, ExternalIdentifiers identifiers) {
        List<ContractPerson> parties = persons.findByContractIdOrderByRole(contract.id());
        return new SubmissionFacts(identifiers, acceptedAt(contract), party(parties, PersonRole.POLICYHOLDER, contract),
                party(parties, PersonRole.INSURED, contract), certificates(contract));
    }

    private Instant acceptedAt(Contract contract) {
        return acceptances.findByContractId(contract.id())
                .map(ContractAcceptance::offer)
                .orElseThrow(() -> new IllegalStateException("у договора КМ " + contract.id() + " нет записи акцепта"))
                .acceptedAt();
    }

    private List<OutboundAttachment> certificates(Contract contract) {
        UUID requestId = contract.state().request().individualRequestId();
        return forms.findByAttachmentIndividualRequestIdAndAttachmentParticipantIdOrderByCreatedAtAscIdAsc(requestId,
                        contract.participantId()).stream()
                .map(KmContractSubmissionFacts::certificate)
                .toList();
    }

    private static OutboundAttachment certificate(PrintedForm form) {
        return new OutboundAttachment(form.id(), form.docType(), form.storedFile()
                .orElseThrow(() -> new IllegalStateException("у записи справки " + form.id() + " нет объекта в хранилище")));
    }

    private static PersonSnapshot party(List<ContractPerson> parties, PersonRole role, Contract contract) {
        return parties.stream()
                .filter(party -> party.role() == role)
                .findFirst()
                .map(ContractPerson::person)
                .orElseThrow(() -> new IllegalStateException(
                        "у договора КМ " + contract.id() + " нет снимка стороны " + role));
    }
}
