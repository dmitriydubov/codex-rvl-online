package ru.projectx.contracts.service.storage;

import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.AttachmentProperties;
import ru.projectx.contracts.exception.AttachmentDefect;
import ru.projectx.contracts.exception.AttachmentRejectedException;

@Component
public class AttachmentFiles {

    private static final String PARAMETERS = ";";

    private final AttachmentProperties settings;
    private final AttachmentMetrics metrics;

    public AttachmentFiles(AttachmentProperties settings, AttachmentMetrics metrics) {
        this.settings = settings;
        this.metrics = metrics;
    }

    public AcceptedFile accept(CertificateUpload upload) {
        String type = upload.declaredType().map(AttachmentFiles::mediaType)
                .filter(settings.contentTypes()::containsKey)
                .orElseThrow(() -> rejected(AttachmentDefect.CONTENT_TYPE));
        if (upload.size() < 1 || upload.size() > settings.maxFileSize().toBytes()) {
            throw rejected(AttachmentDefect.SIZE);
        }
        return new AcceptedFile(type, settings.contentTypes().get(type));
    }

    private static String mediaType(String declared) {
        return Optional.of(declared.indexOf(PARAMETERS)).filter(position -> position >= 0)
                .map(position -> declared.substring(0, position)).orElse(declared)
                .strip().toLowerCase(Locale.ROOT);
    }

    private AttachmentRejectedException rejected(AttachmentDefect defect) {
        metrics.countRejected(defect);
        return new AttachmentRejectedException(defect);
    }

    public record AcceptedFile(String contentType, String extension) {
    }
}
