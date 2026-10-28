package ru.projectx.contracts.service.storage;

import ru.projectx.contracts.entity.document.StoredFile;

public interface CertificateStore {

    void store(StoredFile file, ContentSource content);

    void verifyPresent(String key);
}
