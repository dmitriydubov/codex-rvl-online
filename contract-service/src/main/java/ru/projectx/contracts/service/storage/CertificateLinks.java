package ru.projectx.contracts.service.storage;

import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.StorageProperties;
import ru.projectx.contracts.entity.StoredFile;

@Component
public class CertificateLinks {

    private static final String SEPARATOR = "/";

    private final String bucketPath;

    public CertificateLinks(StorageProperties settings) {
        this.bucketPath = SEPARATOR + settings.bucket() + SEPARATOR;
    }

    public String link(StoredFile file) {
        return bucketPath + file.key();
    }
}
