package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Дано;
import io.cucumber.java.ru.Тогда;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import ru.projectx.contracts.config.storage.StorageProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class AttachmentLifecycleSteps {

    private static final List<String> PRIVATE_FIELDS = List.of(
            "doc_type", "individual_request_id", "participant_id", "policy_id", "family_member_id", "file_hash", "file_key");

    private final AttachmentCalls calls;
    private final CertificateRecords records;
    private final StandStorage storage;
    private final StorageProperties settings;
    private final Map<String, String> snapshots = new HashMap<>();
    private final JsonMapper json = JsonMapper.builder().build();

    public AttachmentLifecycleSteps(AttachmentCalls calls, CertificateRecords records, StandStorage storage,
            StorageProperties settings) {
        this.calls = calls;
        this.records = records;
        this.storage = storage;
        this.settings = settings;
    }

    @Дано("стенд запомнил запись реестра по вложению {string}")
    public void remember(String attachment) {
        snapshots.put(attachment, onlyRow(attachment));
    }

    @Дано("стенд помечает вложение {string} мягко удалённым в {string} с идентификатором запроса {string}")
    public void markDeleted(String attachment, String when, String requestId) {
        assertThat(records.markDeleted(attachment, OffsetDateTime.parse(when), requestId)).isEqualTo(1);
    }

    @Тогда("реестр сохраняет единственную запись по вложению {string} вида {string} без изменений")
    public void unchanged(String attachment, String type) {
        String initial = snapshots.get(attachment);
        assertThat(initial).as("запись реестра до повтора").isNotNull();
        String current = onlyRow(attachment);
        assertThat(json.readTree(current).path("doc_type").asString()).isEqualTo(type);
        assertThat(current).isEqualTo(initial);
    }

    @Тогда("сообщение о конфликте вложения не выдаёт вид вложения, сведения о владельце, имя или содержимое файла")
    public void conflictPrivacy() {
        String response = calls.last().body();
        assertThat(response).doesNotContain("MEDICAL_CERTIFICATE", "PD_CONSENT");
        assertThat(snapshots).isNotEmpty();
        snapshots.values().stream().map(json::readTree).forEach(row -> PRIVATE_FIELDS.stream()
                .map(row::path).filter(JsonNode::isString).forEach(value -> assertThat(response).doesNotContain(value.asString())));
        calls.lastFile().ifPresent(file -> {
            assertThat(response).doesNotContain(file.fileName());
            assertThat(response).doesNotContain(new String(file.content(), StandardCharsets.UTF_8));
        });
        calls.allSentContents().forEach(bytes -> assertThat(response).doesNotContain(new String(bytes, StandardCharsets.UTF_8)));
    }

    @Тогда("параллельные загрузки дали коды ответов {string} в любом порядке")
    public void parallelStatuses(String codes) {
        assertThat(calls.answers()).extracting(AttachmentCalls.Answer::status)
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(codes.split(",")).map(String::strip)
                        .map(Integer::parseInt).toList());
    }

    @Тогда("успешные ответы совпадают по document_id и created_at с единственной записью реестра вида {string} по вложению {string}")
    public void winningRecord(String type, String attachment) {
        assertThat(records.rows(attachment)).hasSize(1);
        CertificateRecords.Certificate saved = records.byAttachment(attachment);
        assertThat(saved.docType()).isEqualTo(type);
        assertThat(calls.answers()).filteredOn(answer -> answer.status() == 200).isNotEmpty().allSatisfy(answer -> {
            JsonNode body = calls.body(answer);
            assertThat(body.path("document_id").asString()).isEqualTo(saved.id().toString());
            assertThat(OffsetDateTime.parse(body.path("created_at").asString()).toInstant()).isEqualTo(saved.createdAt());
        });
        remember(attachment);
    }

    @Тогда("хэш записи и содержимое её объекта соответствуют файлу успешной загрузки")
    public void winningFile() throws Exception {
        assertThat(snapshots).hasSize(1);
        String attachment = snapshots.keySet().iterator().next();
        CertificateRecords.Certificate saved = records.byAttachment(attachment);
        byte[] expected = calls.acceptedContent(attachment);
        assertThat(saved.hash()).isEqualTo(sha256(expected));
        assertThat(storage.object(settings.bucket(), saved.key())).isEqualTo(expected);
    }

    @Тогда("если среди ответов есть конфликт, он имеет код {string} без нарушений по полям")
    public void parallelConflict(String code) {
        assertThat(calls.answers()).filteredOn(answer -> answer.status() == 409).allSatisfy(answer -> {
            JsonNode error = calls.body(answer).path("error");
            assertThat(error.path("code").asString()).isEqualTo(code);
            assertThat(error.path("details").isEmpty()).isTrue();
        });
    }

    private String onlyRow(String attachment) {
        List<String> rows = records.rows(attachment);
        assertThat(rows).hasSize(1);
        return rows.getFirst();
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
