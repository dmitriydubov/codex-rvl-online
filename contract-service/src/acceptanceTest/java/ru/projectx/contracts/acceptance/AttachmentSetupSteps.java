package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Дано;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import ru.projectx.contracts.config.storage.StorageProperties;
import ru.projectx.contracts.entity.document.PrintedForm;
import ru.projectx.contracts.entity.document.PrintedFormType;
import ru.projectx.contracts.entity.document.UskDocument;
import ru.projectx.contracts.entity.party.PersonOwner;
import ru.projectx.contracts.repository.issuance.PrintedFormRepository;

public class AttachmentSetupSteps {

    private final PreparedAttachment prepared;
    private final AttachmentContractFixtures contracts;
    private final PrintedFormRepository forms;
    private final AttachmentRegistryRows registry;
    private final CertificateRecords certificates;
    private final StandStorage storage;
    private final StorageProperties settings;
    private final CertificateStorageSteps storageSteps;
    private final Clock clock;
    private Optional<AttachmentRegistryRows.Entry> previous = Optional.empty();

    public AttachmentSetupSteps(PreparedAttachment prepared, AttachmentContractFixtures contracts,
            PrintedFormRepository forms, AttachmentRegistryRows registry, CertificateRecords certificates,
            StandStorage storage, StorageProperties settings, CertificateStorageSteps storageSteps, Clock clock) {
        this.prepared = prepared;
        this.contracts = contracts;
        this.forms = forms;
        this.registry = registry;
        this.certificates = certificates;
        this.storage = storage;
        this.settings = settings;
        this.storageSteps = storageSteps;
        this.clock = clock;
    }

    @Дано("стенд подготовил вложение {string} в состоянии {string}")
    public void attachment(String kind, String state) throws Exception {
        prepared.upload(kind);
        contracts.state(state);
    }

    @Дано("стенд подготовил справку и договоры пары с историей {string}")
    public void history(String history) throws Exception {
        if ("справка дозагружена после передачи".equals(history)) {
            contracts.state("SUBMITTED");
            prepared.upload("справка PDF");
            contracts.expectNoSubmittedDocuments();
        } else {
            prepared.upload("справка JPEG");
            contracts.history(history);
        }
    }

    @Дано("стенд подготовил идентификатор для скачивания/удаления {string}")
    public void identifier(String kind) throws Exception {
        switch (kind) {
            case "неизвестное вложение" -> prepared.identifier("44d7b966-45eb-41e3-875d-9691790af7df");
            case "печатная форма 1С" -> printedForm();
            case "мягко удалённое вложение" -> deletedAttachment();
            default -> throw new AssertionError("неизвестный вид идентификатора: " + kind);
        }
        previous = registry.lookup(prepared.id());
        if ("неизвестное вложение".equals(kind)) {
            assertThat(previous).isEmpty();
        }
    }

    @io.cucumber.java.ru.Тогда("подготовленное состояние реестра по этому идентификатору не изменилось")
    public void registryUnchanged() {
        assertThat(registry.lookup(prepared.id())).isEqualTo(previous);
    }

    @Дано("стенд удалил объект подготовленного вложения из живого бакета, оставив запись активной")
    public void loseObject() throws Exception {
        storage.removeObject(settings.bucket(), prepared.record().key());
        assertThat(registry.lookup(prepared.id())).get().satisfies(row -> assertThat(row.deletedAt()).isNull());
        assertThat(storage.bucketExists(settings.bucket())).isTrue();
    }

    @Дано("хранилище справок в состоянии {string}")
    public void storageState(String outage) throws Exception {
        if ("без бакета".equals(outage)) {
            storage.removeBucketWithObject(settings.bucket(), prepared.record().key());
            assertThat(storage.bucketExists(settings.bucket())).isFalse();
        } else {
            storageSteps.storageDown(outage);
        }
    }

    private void printedForm() throws Exception {
        contracts.state("ISSUED");
        PrintedForm form = forms.saveAndFlush(PrintedForm.issuedFor(PersonOwner.contract(contracts.contract()),
                new UskDocument(PrintedFormType.CONTRACT, "https://files.example.test/stand.pdf", 42L), clock.instant()));
        prepared.identifier(form.id().toString());
        assertThat(registry.lookup(prepared.id())).get().satisfies(row -> assertThat(row.attachmentId()).isNull());
    }

    private void deletedAttachment() throws Exception {
        prepared.upload("справка JPEG");
        loseObject();
        certificates.markDeleted(prepared.id(), Instant.parse("2026-02-10T07:04:00Z"), "stand-previous-delete");
        assertThat(registry.lookup(prepared.id())).get().satisfies(row -> assertThat(row.deletedAt()).isNotNull());
    }
}
