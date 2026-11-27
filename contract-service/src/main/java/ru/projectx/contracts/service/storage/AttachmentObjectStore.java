package ru.projectx.contracts.service.storage;

import java.io.InputStream;

public interface AttachmentObjectStore {

    InputStream open(String key);

    void remove(String key);
}
