package ru.projectx.contracts.service.integration;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.projectx.contracts.entity.StoredFile;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;
import ru.projectx.contracts.service.storage.CertificateStore;
import ru.projectx.contracts.service.storage.ContentSource;

public class MinioCertificateStore implements CertificateStore {

    private static final Logger log = LoggerFactory.getLogger(MinioCertificateStore.class);
    private static final long AUTOMATIC_PART_SIZE = -1;
    private static final String NO_SUCH_KEY = "NoSuchKey";

    private final MinioClient client;
    private final String bucket;

    public MinioCertificateStore(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void store(StoredFile file, ContentSource content) {
        try (InputStream stream = opened(content)) {
            put(file, stream);
        } catch (IOException closing) {
            throw new UncheckedIOException("файл справки не закрыт после записи", closing);
        }
    }

    @Override
    public byte[] read(String key) {
        GetObjectArgs object = GetObjectArgs.builder().bucket(bucket).object(key).build();
        try (GetObjectResponse stream = client.getObject(object)) {
            return stream.readAllBytes();
        } catch (ErrorResponseException refused) {
            throw refusal(refused);
        } catch (MinioException | IOException | GeneralSecurityException | RuntimeException failure) {
            log.warn("хранилище справок не отдало объект: {}", failure.getClass().getName());
            throw new StorageUnavailableException(failure);
        }
    }

    private static RuntimeException refusal(ErrorResponseException refused) {
        String code = refused.errorResponse().code();
        if (NO_SUCH_KEY.equals(code)) {
            return new CertificateMissingException(refused);
        }
        log.warn("хранилище справок отказало в чтении объекта: код {}", code);
        return new StorageUnavailableException(refused);
    }

    private void put(StoredFile file, InputStream stream) {
        PutObjectArgs object = PutObjectArgs.builder().bucket(bucket).object(file.key())
                .stream(stream, file.size(), AUTOMATIC_PART_SIZE).contentType(file.contentType()).build();
        try {
            client.putObject(object);
        } catch (ErrorResponseException refused) {
            log.warn("хранилище справок отказало в записи объекта: код {}", refused.errorResponse().code());
            throw new StorageUnavailableException(refused);
        } catch (MinioException | IOException | GeneralSecurityException | RuntimeException failure) {
            log.warn("хранилище справок не записало объект: {}", failure.getClass().getName());
            throw new StorageUnavailableException(failure);
        }
    }

    private static InputStream opened(ContentSource content) {
        try {
            return content.open();
        } catch (IOException unreadable) {
            throw new UncheckedIOException("файл справки не прочитан", unreadable);
        }
    }
}
