package ru.projectx.contracts.service.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.projectx.contracts.exception.StorageUnavailableException;

final class AttachmentDownloadStream extends FilterInputStream {

    private static final Logger log = LoggerFactory.getLogger(AttachmentDownloadStream.class);

    private final AttachmentMetrics metrics;

    AttachmentDownloadStream(InputStream source, AttachmentMetrics metrics) {
        super(source);
        this.metrics = metrics;
    }

    @Override
    public int read() throws IOException {
        try {
            return super.read();
        } catch (IOException failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        try {
            return super.read(buffer, offset, length);
        } catch (IOException failure) {
            throw unavailable(failure);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            super.close();
        } catch (IOException failure) {
            throw closingFailure(failure);
        }
    }

    private static IOException closingFailure(IOException failure) {
        return new IOException("поток вложения не закрыт: " + failure.getClass().getSimpleName());
    }

    private StorageUnavailableException unavailable(IOException failure) {
        log.warn("хранилище прервало чтение вложения: {}", failure.getClass().getName());
        metrics.countStorageUnavailable();
        return new StorageUnavailableException();
    }
}
