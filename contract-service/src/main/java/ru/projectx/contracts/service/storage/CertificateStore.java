package ru.projectx.contracts.service.storage;

import ru.projectx.contracts.entity.StoredFile;

public interface CertificateStore {

    void store(StoredFile file, ContentSource content);
}
