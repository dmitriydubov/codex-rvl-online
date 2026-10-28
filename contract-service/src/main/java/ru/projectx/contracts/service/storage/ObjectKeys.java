package ru.projectx.contracts.service.storage;

import java.util.UUID;
import org.springframework.stereotype.Component;
import ru.projectx.contracts.config.storage.StorageProperties;

@Component
public class ObjectKeys {

    private static final String SEPARATOR = "/";
    private static final String EXTENSION = ".";
    private static final String ORGANIZATION_SEPARATOR = "_";

    private final String basePath;

    public ObjectKeys(StorageProperties settings) {
        this.basePath = settings.inn() + ORGANIZATION_SEPARATOR + settings.kpp() + SEPARATOR + settings.basePath();
    }

    public String next(UUID attachmentId, String extension) {
        return basePath + SEPARATOR + UUID.randomUUID() + SEPARATOR + attachmentId + EXTENSION + extension;
    }
}
