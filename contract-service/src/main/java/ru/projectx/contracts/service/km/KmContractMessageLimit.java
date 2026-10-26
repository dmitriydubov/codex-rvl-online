package ru.projectx.contracts.service.km;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.KmContractSubmissionProperties;
import ru.projectx.contracts.exception.MessageTooLargeException;
import ru.projectx.contracts.message.CertificateDocument;
import ru.projectx.contracts.service.outbox.OutboundAttachment;

@Component
public class KmContractMessageLimit {

    static final int CONTENT_FIELD_OVERHEAD = (",\"" + CertificateDocument.CONTENT_FIELD + "\":\"\"").length();

    private static final int BASE64_INPUT_BLOCK = 3;
    private static final int BASE64_OUTPUT_BLOCK = 4;

    private final long limit;

    public KmContractMessageLimit(KmContractSubmissionProperties properties) {
        this.limit = properties.maxMessageSize().toBytes();
    }

    public void verify(String payload, List<OutboundAttachment> certificates) {
        long size = size(payload, certificates);
        if (size > limit) {
            throw new MessageTooLargeException(size, limit, certificates.size());
        }
    }

    long size(String payload, List<OutboundAttachment> certificates) {
        long contents = certificates.stream()
                .mapToLong(certificate -> CONTENT_FIELD_OVERHEAD + base64Length(certificate.file().size()))
                .sum();
        return payload.getBytes(StandardCharsets.UTF_8).length + contents;
    }

    static long base64Length(long bytes) {
        return BASE64_OUTPUT_BLOCK * ((bytes + BASE64_INPUT_BLOCK - 1) / BASE64_INPUT_BLOCK);
    }
}
