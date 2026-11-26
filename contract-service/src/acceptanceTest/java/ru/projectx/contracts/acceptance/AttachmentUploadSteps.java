package ru.projectx.contracts.acceptance;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.ru.Когда;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

public class AttachmentUploadSteps {

    private static final String ATTACHMENT = "3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21";
    private static final String OWNER = "61a7982c-a0b4-4bbf-9527-60b40b752803";
    private static final String PERSON = "9275471c-d2fe-484f-97a6-d6d7b1267b5e";
    private static final String OTHER_OWNER = "0f4e2a9c-6b18-4c73-a5d0-9e2b7f1c3a66";
    private static final String OTHER_PERSON = "6778e8c2-985a-4d54-9bd3-0f6403b104d6";
    private static final String FILE_NAME = "Тестова_Анна_070у.jpg";
    private static final String CONSENT_NAME = "stand-attachment.pdf";
    private static final String CONTENT = "СТЕНД: вымышленная справка 070/у, участник 1";
    private static final String FIRST_CONSENT = "STAND CONSENT A";
    private static final String SECOND_CONSENT = "STAND CONSENT BB";
    private static final String JPEG = "image/jpeg";
    private static final String NOT_A_GUID = "not-a-guid";
    private static final byte[] FILLER = "070u".getBytes(StandardCharsets.US_ASCII);
    private static final int OVER_LIMIT = 5_242_881;

    private static final Map<String, UnaryOperator<AttachmentRequest>> DEFECTS = Map.ofEntries(
            Map.entry("тип файла image/tiff", request -> type(request, "image/tiff")),
            Map.entry("тип файла image/gif", request -> type(request, "image/gif")),
            Map.entry("тип файла application/octet-stream", request -> type(request, "application/octet-stream")),
            Map.entry("тип файла не указан", request -> type(request, null)),
            Map.entry("пустой файл", request -> bytes(request, new byte[0])),
            Map.entry("файл на байт больше предела", request -> bytes(request, filler(OVER_LIMIT))),
            Map.entry("нет части attachment_id", request -> request.withAttachmentPart(Optional.empty())),
            Map.entry("attachment_id — не GUID", request -> request.withAttachmentPart(Optional.of(NOT_A_GUID))),
            Map.entry("attachment_id пустой", request -> request.withAttachmentPart(Optional.of(""))),
            Map.entry("attachment_id с именем файла", request -> request.withAttachmentFilename("stand-id.txt")),
            Map.entry("attachment_id с пустым именем файла", request -> request.withAttachmentFilename("")),
            Map.entry("нет части file", request -> request.withFile(Optional.empty())),
            Map.entry("file без имени файла", request -> name(request, null)),
            Map.entry("file с пустым именем файла", request -> name(request, "")),
            Map.entry("file с пробельным именем файла", request -> name(request, "   ")),
            Map.entry("тело не multipart", AttachmentRequest::asJson));

    private final AttachmentCalls calls;

    public AttachmentUploadSteps(AttachmentCalls calls) {
        this.calls = calls;
    }

    @Когда("сервис Заявок загружает справку участника {string} заявки {string}:")
    public void upload(String participant, String request, DataTable table) throws IOException, InterruptedException {
        calls.send(request(AttachmentEndpoint.CERTIFICATE.path(request, participant), table.asMap()));
    }

    @Когда("сервис Заявок загружает справку участника {string} заявки {string} с дефектом {string}")
    public void uploadWithDefect(String participant, String request, String defect)
            throws IOException, InterruptedException {
        calls.send(defective(AttachmentEndpoint.CERTIFICATE, request, participant, defect));
    }

    @Когда("сервис Заявок загружает согласие члена семьи {string} по полису {string}:")
    public void uploadConsent(String member, String policy, DataTable table) throws IOException, InterruptedException {
        calls.send(request(AttachmentEndpoint.CONSENT.path(policy, member), table.asMap()));
    }

    @Когда("сервис Заявок загружает согласие члена семьи {string} по полису {string} с дефектом {string}")
    public void consentWithDefect(String member, String policy, String defect) throws IOException, InterruptedException {
        calls.send(defective(AttachmentEndpoint.CONSENT, policy, member, defect));
    }

    @Когда("сервис Заявок загружает вложение через {string}:")
    public void uploadByOperation(String operation, DataTable table) throws IOException, InterruptedException {
        calls.send(request(AttachmentEndpoint.fromOperation(operation).path(OWNER, PERSON), table.asMap()));
    }

    @Когда("сервис Заявок повторяет загрузку через {string} с отличием {string}")
    public void uploadWithDifference(String operation, String difference) throws IOException, InterruptedException {
        AttachmentEndpoint endpoint = AttachmentEndpoint.fromOperation(operation);
        AttachmentRequest changed = switch (difference) {
            case "первый идентификатор владельца" -> operationRequest(endpoint.path(OTHER_OWNER, PERSON), ATTACHMENT, FIRST_CONSENT);
            case "второй идентификатор владельца" -> operationRequest(endpoint.path(OWNER, OTHER_PERSON), ATTACHMENT, FIRST_CONSENT);
            case "байты файла" -> operationRequest(endpoint.path(OWNER, PERSON), ATTACHMENT, SECOND_CONSENT);
            default -> throw new AssertionError("неизвестное отличие " + difference);
        };
        calls.send(changed);
    }

    @Когда("сервис Заявок отправляет две параллельные загрузки через {string} по вложению {string}, во второй загрузке — {string}")
    public void parallelUploads(String operation, String attachment, String second) {
        String path = AttachmentEndpoint.fromOperation(operation).path(OWNER, PERSON);
        String content = switch (second) {
            case "тот же файл" -> FIRST_CONSENT;
            case "другой файл" -> SECOND_CONSENT;
            default -> throw new AssertionError("неизвестное содержимое второго файла " + second);
        };
        calls.sendTogether(operationRequest(path, attachment, FIRST_CONSENT), operationRequest(path, attachment, content));
    }

    @Когда("сервис Заявок загружает справку участника {string} заявки {string} с заголовком X-Request-Id {string}:")
    public void uploadWithRequestId(String participant, String request, String requestId, DataTable table)
            throws IOException, InterruptedException {
        calls.send(request(AttachmentEndpoint.CERTIFICATE.path(request, participant), table.asMap()).withRequestId(requestId));
    }

    private static AttachmentRequest request(String path, Map<String, String> fields) {
        byte[] content = Optional.ofNullable(fields.get("содержимое")).map(AttachmentUploadSteps::content)
                .orElseGet(() -> filler(Integer.parseInt(required(fields, "размер"))));
        return new AttachmentRequest(path, Optional.of(required(fields, "вложение")),
                Optional.of(new AttachmentRequest.FilePart(required(fields, "имя файла"), required(fields, "тип"),
                        content)), Optional.empty(), Optional.empty(), true);
    }

    private static AttachmentRequest defective(AttachmentEndpoint endpoint, String owner, String person, String defect) {
        return switch (defect) {
            case "идентификатор заявки в пути — не GUID", "идентификатор полиса в пути — не GUID" ->
                    standard(endpoint, endpoint.path(NOT_A_GUID, person));
            case "идентификатор участника в пути — не GUID", "идентификатор члена семьи в пути — не GUID" ->
                    standard(endpoint, endpoint.path(owner, NOT_A_GUID));
            default -> Optional.ofNullable(DEFECTS.get(defect))
                    .orElseThrow(() -> new AssertionError("дефекта «" + defect + "» нет в перечне шага"))
                    .apply(standard(endpoint, endpoint.path(owner, person)));
        };
    }

    private static AttachmentRequest standard(AttachmentEndpoint endpoint, String path) {
        if (endpoint == AttachmentEndpoint.CONSENT) {
            return operationRequest(path, ATTACHMENT, FIRST_CONSENT);
        }
        return multipart(path, ATTACHMENT, new AttachmentRequest.FilePart(FILE_NAME, JPEG, content(CONTENT)));
    }

    private static AttachmentRequest operationRequest(String path, String attachment, String value) {
        return multipart(path, attachment, new AttachmentRequest.FilePart(CONSENT_NAME, "application/pdf", content(value)));
    }

    private static AttachmentRequest multipart(String path, String attachment, AttachmentRequest.FilePart file) {
        return new AttachmentRequest(path, Optional.of(attachment), Optional.of(file), Optional.empty(), Optional.empty(), true);
    }

    private static AttachmentRequest type(AttachmentRequest request, @Nullable String contentType) {
        return request.withFile(request.filePart().map(file ->
                new AttachmentRequest.FilePart(file.fileName(), contentType, file.content())));
    }

    private static AttachmentRequest bytes(AttachmentRequest request, byte[] content) {
        return request.withFile(request.filePart().map(file ->
                new AttachmentRequest.FilePart(file.fileName(), file.contentType(), content)));
    }

    private static AttachmentRequest name(AttachmentRequest request, @Nullable String name) {
        return request.withFile(request.filePart().map(file ->
                new AttachmentRequest.FilePart(name, file.contentType(), file.content())));
    }

    private static String required(Map<String, String> fields, String name) {
        return Optional.ofNullable(fields.get(name))
                .orElseThrow(() -> new AssertionError("в таблице загрузки нет строки «" + name + "»"));
    }

    private static byte[] content(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] filler(int size) {
        byte[] content = new byte[size];
        for (int index = 0; index < size; index++) {
            content[index] = FILLER[index % FILLER.length];
        }
        return content;
    }
}
