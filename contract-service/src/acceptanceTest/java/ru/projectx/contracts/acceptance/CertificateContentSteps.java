package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.test.web.server.LocalManagementPort;
import ru.projectx.contracts.config.StorageProperties;

public class CertificateContentSteps {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String CONTENT_FIELD = "\"content_base64\"";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final CertificateRecords certificates;
    private final OutboxRecords journal;
    private final AttachmentCalls calls;
    private final StandStorage storage;
    private final StorageProperties settings;
    private final ServiceJournal serviceJournal;
    private final URI metrics;

    public CertificateContentSteps(CertificateRecords certificates, OutboxRecords journal, AttachmentCalls calls,
            StandStorage storage, StorageProperties settings, ServiceJournal serviceJournal,
            @LocalManagementPort int managementPort) {
        this.certificates = certificates;
        this.journal = journal;
        this.calls = calls;
        this.storage = storage;
        this.settings = settings;
        this.serviceJournal = serviceJournal;
        this.metrics = URI.create("http://localhost:" + managementPort + "/actuator/prometheus");
    }

    @Тогда("тело исходящего сообщения №{int} по заявке {string} не содержит содержимого справок")
    public void payloadWithoutContents(int ordinal, String requestId) {
        String payload = journal.number(ordinal, requestId).payload();
        assertThat(payload).as("тело исходящего сообщения №%s", ordinal).isNotNull().doesNotContain(CONTENT_FIELD);
        for (byte[] content : calls.allSentContents()) {
            assertThat(payload).doesNotContain(new String(content, StandardCharsets.UTF_8))
                    .doesNotContain(Base64.getEncoder().encodeToString(content));
        }
    }

    @Дано("объект справки по вложению {string} удалён из хранилища")
    public void objectRemoved(String attachment) throws Exception {
        storage.removeObject(settings.bucket(), certificates.byAttachment(attachment).key());
    }

    @Когда("стенд возвращает в хранилище объект справки по вложению {string}")
    public void objectRestored(String attachment) throws Exception {
        storage.putObject(settings.bucket(), certificates.byAttachment(attachment).key(), calls.sentContent(attachment));
    }

    @Дано("справка по вложению {string} загружена до перехода на ключи с ИНН и КПП: стенд перенёс её объект и ключ в реестре на {string}")
    public void storedUnderTheEarlierKey(String attachment, String earlierKey) throws Exception {
        String key = certificates.byAttachment(attachment).key();
        storage.putObject(settings.bucket(), earlierKey, storage.object(settings.bucket(), key));
        storage.removeObject(settings.bucket(), key);
        certificates.rekey(attachment, earlierKey);
    }

    @Тогда("имя файла {string} и содержимое справки по вложению {string} не попали ни в журнал Договоров, ни в метрики, ни в причины исходов журнала исходящих")
    public void medicalDataStaysOutOfOutcomes(String fileName, String attachment) throws IOException, InterruptedException {
        String content = new String(calls.sentContent(attachment), StandardCharsets.UTF_8);
        String exposition = exposition();
        for (String value : new String[] {fileName, content}) {
            assertThat(serviceJournal.mentioning(value)).extracting(ILoggingEvent::getFormattedMessage)
                    .as("записи журнала с «%s»", value).isEmpty();
            assertThat(exposition).as("выдача метрик").doesNotContain(value);
            assertThat(journal.all()).extracting(OutboxRecords.Entry::errorMessage)
                    .as("причины исходов журнала исходящих")
                    .allSatisfy(reason -> assertThat(reason == null || !reason.contains(value)).isTrue());
        }
    }

    private String exposition() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(metrics).timeout(TIMEOUT).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).as("ответ /actuator/prometheus").isEqualTo(200);
        return response.body();
    }
}
