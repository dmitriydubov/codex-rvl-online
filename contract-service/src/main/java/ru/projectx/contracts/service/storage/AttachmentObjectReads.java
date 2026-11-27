package ru.projectx.contracts.service.storage;

import java.io.InputStream;

public interface AttachmentObjectReads {

    InputStream open(String key);
}
