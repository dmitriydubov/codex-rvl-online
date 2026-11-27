package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.ru.Тогда;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import ru.projectx.contracts.config.storage.StorageProperties;

public class AttachmentAccessAssertions {

    private final PreparedAttachment prepared;
    private final AttachmentCalls calls;
    private final AttachmentRegistryRows registry;
    private final CertificateRecords certificates;
    private final StandStorage storage;
    private final StorageProperties settings;
    private final ServiceJournal journal;
    private final MedicalDataSteps metrics;

    public AttachmentAccessAssertions(PreparedAttachment prepared, AttachmentCalls calls,
            AttachmentRegistryRows registry, CertificateRecords certificates, StandStorage storage,
            StorageProperties settings, ServiceJournal journal, MedicalDataSteps metrics) {
        this.prepared = prepared;
        this.calls = calls;
        this.registry = registry;
        this.certificates = certificates;
        this.storage = storage;
        this.settings = settings;
        this.journal = journal;
        this.metrics = metrics;
    }

    @Тогда("в реестре у подготовленного вложения отметки удаления:")
    public void deletionAudit(DataTable marks) {
        Map<String, String> expected = marks.asMap();
        var entry = registry.lookup(prepared.id()).orElseThrow();
        assertThat(Objects.requireNonNull(entry.deletedAt()).toInstant())
                .isEqualTo(Instant.parse(expected.get("deleted_at")));
        assertThat(entry.deletedRequestId()).isEqualTo(expected.get("deleted_request_id"));
        assertThat(certificates.byAttachment(prepared.id()).equals(prepared.record())).isTrue();
    }

    @Тогда("запись подготовленного вложения осталась активной")
    public void active() {
        var entry = registry.lookup(prepared.id()).orElseThrow();
        assertThat(entry.deletedAt()).isNull();
        assertThat(entry.deletedRequestId()).isNull();
        assertThat(certificates.byAttachment(prepared.id()).equals(prepared.record())).isTrue();
    }

    @Тогда("объекта подготовленного вложения больше нет в хранилище")
    public void objectGone() throws Exception {
        String key = prepared.record().key();
        assertThat(storage.keys(settings.bucket(), key).contains(key)).isFalse();
    }

    @Тогда("содержимое объекта подготовленного вложения в хранилище совпадает с загруженным файлом")
    public void objectIntact() throws Exception {
        assertThat(storage.object(settings.bucket(), prepared.record().key())).isEqualTo(prepared.bytes());
    }

    @Тогда("в журнале Договоров есть запись о вызове {string} с X-Request-Id ответа и кодом {int}")
    public void callLogged(String method, int status) {
        assertThat(requestEvents()).extracting(ILoggingEvent::getFormattedMessage)
                .contains("синхронный вызов " + method + " /internal/v1/attachments/" + prepared.id()
                        + ("GET".equals(method) ? "/content" : "") + " завершён с кодом " + status);
    }

    @Тогда("в журнале Договоров есть запись уровня {string} об отсутствующем объекте с идентификатором вложения")
    public void missingObjectLogged(String level) {
        assertThat(requestEvents()).anySatisfy(event -> {
            assertThat(event.getLevel().toString()).isEqualTo(level);
            assertThat(event.getFormattedMessage()).isEqualTo("в хранилище отсутствует объект вложения " + prepared.id());
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Тогда("имя файла, ключ объекта и содержимое подготовленного вложения не попали в журнал Договоров и в метрики")
    public void sensitiveDataDidNotLeak() throws Exception {
        String exposition = metrics.exposition();
        for (String value : sensitiveValues()) {
            assertThat(journal.mentioning(value)).isEmpty();
            assertThat(exposition).doesNotContain(value);
        }
    }

    @Тогда("имя файла, ключ объекта и содержимое подготовленного вложения не попали в журнал Договоров, метрики и ответ с ошибкой")
    public void errorDoesNotExposeSensitiveData() throws Exception {
        sensitiveDataDidNotLeak();
        assertThat(calls.last().body()).doesNotContain(sensitiveValues());
    }

    private List<ILoggingEvent> requestEvents() {
        String requestId = calls.last().requestId().orElseThrow();
        return journal.events().stream().filter(event -> requestId.equals(event.getMDCPropertyMap().get("requestId")))
                .toList();
    }

    private List<String> sensitiveValues() {
        byte[] bytes = prepared.bytes();
        return List.of(Objects.requireNonNull(prepared.uploadRequest().filePart().orElseThrow().fileName()),
                prepared.record().key(), "K16b", new String(bytes, StandardCharsets.UTF_8),
                HexFormat.of().formatHex(bytes), Base64.getEncoder().encodeToString(bytes));
    }
}
