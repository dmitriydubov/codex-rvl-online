package ru.projectx.contracts.service.km;

import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.entity.Contract;
import ru.projectx.contracts.entity.PrintedForm;
import ru.projectx.contracts.repository.PrintedFormRepository;

@Component
public class InsuredCertificates {

    private final PrintedFormRepository forms;

    public InsuredCertificates(PrintedFormRepository forms) {
        this.forms = forms;
    }

    public List<SubmittedCertificate> of(Contract contract) {
        return forms.findByAttachmentIndividualRequestIdAndAttachmentParticipantIdOrderByCreatedAtAscIdAsc(
                        contract.state().request().individualRequestId(), contract.participantId()).stream()
                .map(InsuredCertificates::certificate)
                .toList();
    }

    private static SubmittedCertificate certificate(PrintedForm form) {
        return new SubmittedCertificate(form.id(), form.docType(), form.storedFile()
                .orElseThrow(() -> new IllegalStateException("у записи справки " + form.id() + " нет объекта в хранилище")));
    }
}
