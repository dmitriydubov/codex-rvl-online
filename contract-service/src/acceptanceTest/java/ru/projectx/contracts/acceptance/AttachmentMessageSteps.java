package ru.projectx.contracts.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ru.Тогда;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AttachmentMessageSteps {

    private final ReceivedLmPolicyRequests requests;
    private final CertificateRecords records;

    public AttachmentMessageSteps(ReceivedLmPolicyRequests requests, CertificateRecords records) {
        this.requests = requests;
        this.records = records;
    }

    @Тогда("запрос на выпуск полиса ЛМ №{int} не содержит реквизитов справок и согласий")
    public void attachmentDetailsAreAbsent(int ordinal) {
        String payload = new String(requests.number(ordinal).getBody(), StandardCharsets.UTF_8);
        List<CertificateRecords.Certificate> attachments = records.attachedDocuments();
        assertThat(attachments).extracting(CertificateRecords.Certificate::docType)
                .contains("MEDICAL_CERTIFICATE", "PD_CONSENT");
        for (CertificateRecords.Certificate attachment : attachments) {
            assertThat(payload).doesNotContain(attachment.id().toString(), attachment.attachment().toString(),
                    attachment.docType(), attachment.hash(), attachment.key());
        }
    }
}
