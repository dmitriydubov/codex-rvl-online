package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.spring.ScenarioScope;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;

@Component
@ScenarioScope(proxyMode = ScopedProxyMode.NO)
class PreparedAttachment {

    private static final String ATTACHMENT = "3c2f8e10-5a7b-4d21-9f6e-1b0a7c4d8e21";
    private static final String API = "/internal/v1";
    private static final String CERTIFICATE = API + "/requests/" + AttachmentContractFixtures.REQUEST + "/participants/"
            + AttachmentContractFixtures.PARTICIPANT + "/attachments";
    private static final String CONSENT = API + "/policies/" + AttachmentContractFixtures.REQUEST + "/family-members/"
            + AttachmentContractFixtures.PARTICIPANT + "/consents";

    private static final Map<String, String> PATHS = Map.of("справка", CERTIFICATE, "согласие", CONSENT);
    private static final Map<String, File> FILES = Map.of(
            "JPEG", new File("jpg", "image/jpeg", "ffd8fffe004b313662ffd9"),
            "PNG", new File("png", "image/png", "89504e470d0a1a0a4b31366200"),
            "PDF", new File("pdf", "application/pdf", "255044462d312e370a25e2e3cfd30a4b313662"));

    private final AttachmentCalls calls;
    private final CertificateRecords certificates;
    private Optional<String> identifier = Optional.empty();
    private Optional<AttachmentRequest> request = Optional.empty();
    private Optional<CertificateRecords.Certificate> record = Optional.empty();

    PreparedAttachment(AttachmentCalls calls, CertificateRecords certificates) {
        this.calls = calls;
        this.certificates = certificates;
    }

    void upload(String variant) throws Exception {
        String[] parts = variant.split(" ", -1);
        assertThat(parts).as("вид и формат вложения").hasSize(2);
        assertThat(PATHS).containsKey(parts[0]);
        assertThat(FILES).containsKey(parts[1]);
        File file = FILES.get(parts[1]);
        var attachment = new AttachmentRequest.AttachmentPart(ATTACHMENT, null);
        var multipart = new AttachmentRequest.FilePart("Стенд_вложение." + file.extension(),
                file.contentType(), HexFormat.of().parseHex(file.hex()));
        var upload = new AttachmentRequest(PATHS.get(parts[0]), Optional.of(attachment), Optional.of(multipart),
                Optional.empty(), true);
        calls.send(upload);
        assertThat(calls.last().status()).as("код загрузки вложения").isEqualTo(200);
        identifier(ATTACHMENT);
        request = Optional.of(upload);
        record = Optional.of(certificates.byAttachment(ATTACHMENT));
        assertThat(record().contentType()).isEqualTo(file.contentType());
    }

    void identifier(String value) {
        identifier = Optional.of(value);
    }

    String id() {
        return identifier.orElseThrow(() -> new AssertionError("идентификатор вложения не подготовлен"));
    }

    AttachmentRequest uploadRequest() {
        return request.orElseThrow(() -> new AssertionError("загрузка вложения не подготовлена"));
    }

    CertificateRecords.Certificate record() {
        return record.orElseThrow(() -> new AssertionError("запись загруженного вложения не подготовлена"));
    }

    byte[] bytes() {
        return calls.acceptedContent(id());
    }

    private record File(String extension, String contentType, String hex) {
    }
}
