package ru.projectx.contracts.service.integration.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioAsyncClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;
import ru.projectx.contracts.service.storage.AttachmentObjectDeletes;
import ru.projectx.contracts.service.storage.AttachmentObjectReads;

public class MinioAttachmentStore implements AttachmentObjectReads, AttachmentObjectDeletes {

    private static final Logger log = LoggerFactory.getLogger(MinioAttachmentStore.class);

    private final MinioAsyncClient client;
    private final String bucket;

    public MinioAttachmentStore(MinioAsyncClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public InputStream open(String key) {
        GetObjectArgs object = GetObjectArgs.builder().bucket(bucket).object(key).build();
        try {
            return awaitContent(client.getObject(object));
        } catch (InterruptedException interruption) {
            throw interrupted(interruption);
        } catch (ExecutionException | MinioException | IOException | GeneralSecurityException | RuntimeException failure) {
            throw openFailure(failure);
        }
    }

    @Override
    public void remove(String key) {
        RemoveObjectArgs object = RemoveObjectArgs.builder().bucket(bucket).object(key).build();
        await(() -> client.removeObject(object));
    }

    private static InputStream awaitContent(CompletableFuture<GetObjectResponse> pending)
            throws ExecutionException, InterruptedException {
        try {
            return pending.get();
        } catch (InterruptedException interrupted) {
            pending.thenAccept(MinioAttachmentStore::closeAbandoned);
            throw interrupted;
        }
    }

    private RuntimeException openFailure(Exception failure) {
        Throwable cause = operationFailure(failure);
        if (missingKey(cause) && bucketExists()) {
            return new CertificateMissingException();
        }
        return unavailable(cause);
    }

    private static boolean missingKey(Throwable failure) {
        return failure instanceof ErrorResponseException refused && "NoSuchKey".equals(refused.errorResponse().code());
    }

    private boolean bucketExists() {
        return await(() -> client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()));
    }

    private static <T> T await(Operation<T> operation) {
        try {
            return operation.start().get();
        } catch (InterruptedException interrupted) {
            throw interrupted(interrupted);
        } catch (ExecutionException | MinioException | IOException | GeneralSecurityException | RuntimeException failure) {
            throw unavailable(failure);
        }
    }

    private static Throwable operationFailure(Exception failure) {
        return failure instanceof ExecutionException execution ? execution.getCause() : failure;
    }

    private static void closeAbandoned(GetObjectResponse response) {
        try {
            response.close();
        } catch (IOException failure) {
            log.warn("поток вложения не закрыт: {}", failure.getClass().getName());
        }
    }

    private static StorageUnavailableException interrupted(InterruptedException failure) {
        Thread.currentThread().interrupt();
        return unavailable(failure);
    }

    private static StorageUnavailableException unavailable(Throwable failure) {
        log.warn("хранилище вложений не выполнило операцию: {}", failure.getClass().getName());
        return new StorageUnavailableException();
    }

    @FunctionalInterface
    private interface Operation<T> {
        CompletableFuture<T> start() throws MinioException, IOException, GeneralSecurityException;
    }
}
