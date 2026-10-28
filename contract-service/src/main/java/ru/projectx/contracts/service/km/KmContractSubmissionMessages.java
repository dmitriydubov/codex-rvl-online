package ru.projectx.contracts.service.km;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.AttachmentProperties;
import ru.projectx.contracts.entity.contract.Contract;
import ru.projectx.contracts.entity.party.PersonSnapshot;
import ru.projectx.contracts.entity.document.StoredFile;
import ru.projectx.contracts.message.usk.CertificateDocument;
import ru.projectx.contracts.message.usk.KmContractEvents;
import ru.projectx.contracts.message.usk.KmContractPerson;
import ru.projectx.contracts.message.usk.KmContractSubmission;
import ru.projectx.contracts.message.usk.KmContractSubmitted;
import ru.projectx.contracts.message.MessageMeta;
import ru.projectx.contracts.message.status.StatusEvents;
import ru.projectx.contracts.service.storage.CertificateLinks;
import ru.projectx.contracts.service.usk.ParticipantDocuments;
import ru.projectx.contracts.service.usk.RequestTerms;
import ru.projectx.contracts.service.usk.UskRequestMessages;
import tools.jackson.databind.json.JsonMapper;

@Component
public class KmContractSubmissionMessages implements UskRequestMessages<Contract, SubmissionFacts> {

    private static final String FILE_NAME_PREFIX = "certificate-";

    private final JsonMapper json;
    private final Map<String, String> extensions;
    private final CertificateLinks links;

    public KmContractSubmissionMessages(JsonMapper json, AttachmentProperties attachments, CertificateLinks links) {
        this.json = json;
        this.extensions = attachments.contentTypes();
        this.links = links;
    }

    @Override
    public MessageMeta meta(Contract contract, Instant now) {
        return new MessageMeta(UUID.randomUUID(), contract.state().request().individualRequestId(),
                KmContractEvents.KM_CONTRACT_SUBMISSION_TOPIC, KmContractEvents.KM_CONTRACT_SUBMITTED,
                StatusEvents.SCHEMA_VERSION, utc(now), contract.state().entityVersion(), StatusEvents.SOURCE);
    }

    @Override
    public String write(MessageMeta meta, Contract contract, SubmissionFacts facts) {
        KmContractSubmission data = new KmContractSubmission(contract.id(), contract.number(),
                RequestTerms.of(contract.state(), facts.identifiers()), contract.premiumAmount(), utc(paidAt(contract)),
                utc(facts.acceptedAt()), person(facts.policyholder(), null, null), insured(contract, facts.insured()),
                facts.certificates().stream().map(this::document).toList());
        return json.writeValueAsString(new KmContractSubmitted(meta, data));
    }

    private static OffsetDateTime utc(Instant moment) {
        return OffsetDateTime.ofInstant(moment, ZoneOffset.UTC);
    }

    private static Instant paidAt(Contract contract) {
        return contract.progress().paidAt()
                .orElseThrow(() -> new IllegalStateException("у договора КМ " + contract.id() + " нет момента оплаты"));
    }

    private static KmContractPerson insured(Contract contract, PersonSnapshot person) {
        if (contract.participantId().equals(contract.state().policyholderParticipantId())) {
            return person(person, false, null);
        }
        return person(person, true, person.relationType());
    }

    private static KmContractPerson person(PersonSnapshot person, @Nullable Boolean isRelative,
            @Nullable String relationType) {
        return new KmContractPerson(person.participantId(), isRelative, relationType, person.lastName(),
                person.firstName(), person.middleName(), person.birthDate(), person.gender().name(),
                ParticipantDocuments.of(person.document()), person.address());
    }

    private CertificateDocument document(SubmittedCertificate certificate) {
        StoredFile file = certificate.file();
        return new CertificateDocument(certificate.documentId(), certificate.docType().name(),
                fileName(certificate.documentId(), file.contentType()), file.contentType(), links.link(file), file.hash());
    }

    private String fileName(UUID documentId, String contentType) {
        String extension = Optional.ofNullable(extensions.get(contentType))
                .orElseThrow(() -> new IllegalStateException(
                        "тип содержимого справки " + documentId + " не входит в допустимые"));
        return FILE_NAME_PREFIX + documentId + "." + extension;
    }
}
