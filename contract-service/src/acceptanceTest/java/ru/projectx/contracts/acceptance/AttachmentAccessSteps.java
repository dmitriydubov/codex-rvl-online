package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.ru.Когда;
import io.cucumber.java.ru.Тогда;
import java.util.Optional;
import java.util.UUID;

public class AttachmentAccessSteps {

    private static final String PATH = "/internal/v1/attachments/";
    private static final String CONTENT = "/content";
    private static final String READ_REQUEST_ID = "k16b-read-test";

    private final PreparedAttachment prepared;
    private final AttachmentCalls calls;
    private final StandStorage storage;
    private long activityBeforeCall;

    public AttachmentAccessSteps(PreparedAttachment prepared, AttachmentCalls calls, StandStorage storage) {
        this.prepared = prepared;
        this.calls = calls;
        this.storage = storage;
    }

    @Когда("сервис Заявок запрашивает содержимое подготовленного вложения, заголовок X-Request-Id {string}")
    public void readTraced(String header) throws Exception {
        Optional<String> requestId = switch (header) {
            case "передан" -> Optional.of(READ_REQUEST_ID);
            case "отсутствует" -> Optional.empty();
            default -> throw new AssertionError("неизвестный режим заголовка: " + header);
        };
        exchange("GET", prepared.id() + CONTENT, requestId);
    }

    @Когда("сервис Заявок запрашивает содержимое по подготовленному идентификатору")
    public void readPrepared() throws Exception {
        readId(prepared.id());
    }

    @Когда("сервис Заявок запрашивает содержимое вложения с идентификатором {string}")
    public void readId(String identifier) throws Exception {
        exchange("GET", identifier + CONTENT, Optional.empty());
    }

    @Когда("сервис Заявок удаляет подготовленное вложение с заголовком X-Request-Id {string}")
    public void deleteTraced(String requestId) throws Exception {
        exchange("DELETE", prepared.id(), Optional.of(requestId));
    }

    @Когда("сервис Заявок удаляет вложение по подготовленному идентификатору")
    public void deletePrepared() throws Exception {
        deleteId(prepared.id());
    }

    @Когда("сервис Заявок удаляет вложение с идентификатором {string}")
    public void deleteId(String identifier) throws Exception {
        exchange("DELETE", identifier, Optional.empty());
    }

    @Когда("сервис Заявок повторяет загрузку подготовленного вложения")
    public void reupload() throws Exception {
        calls.send(prepared.uploadRequest());
    }

    @Тогда("Договоры ответили на скачивание {int}, тело побайтово совпадает с загруженным файлом")
    public void downloaded(int status) {
        assertThat(calls.last().status()).isEqualTo(status);
        assertThat(calls.last().bytes()).isEqualTo(prepared.bytes());
    }

    @Тогда("Договоры ответили на удаление {int}, тело ответа пустое")
    public void deleted(int status) {
        assertThat(calls.last().status()).isEqualTo(status);
        assertThat(calls.last().bytes()).isEmpty();
    }

    @Тогда("в ответе на скачивание заголовки:")
    public void downloadHeaders(DataTable headers) {
        headers.asMap().forEach((name, expected) ->
                assertThat(calls.last().headers().firstValue(name)).as(name).contains(expected));
    }

    @Тогда("в ответе на скачивание X-Request-Id — {string}")
    public void downloadRequestId(String mode) {
        AttachmentCalls.Answer answer = calls.last();
        String issued = answer.requestId().orElseThrow();
        switch (mode) {
            case "сквозной" -> assertThat(answer.sentRequestId()).contains(issued);
            case "новый UUID" -> {
                assertThat(UUID.fromString(issued)).hasToString(issued);
                assertThat(answer.sentRequestId()).isNotEqualTo(answer.requestId());
            }
            default -> throw new AssertionError("неизвестный режим идентификатора: " + mode);
        }
    }

    @Тогда("Договоры не обращались к S3 за содержимым вложения")
    @Тогда("Договоры не обращались к S3 за удалением вложения")
    public void noStorageAccess() {
        assertThat(storage.serviceActivity()).as("сетевые обращения клиента к S3").isEqualTo(activityBeforeCall);
    }

    private void exchange(String method, String resource, Optional<String> requestId) throws Exception {
        activityBeforeCall = storage.serviceActivity();
        calls.exchange(method, PATH + resource, requestId, Optional.empty());
    }
}
