package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.ServerException;
import io.minio.messages.ErrorResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.projectx.contracts.entity.StoredFile;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;
import ru.projectx.contracts.service.storage.ContentSource;

class MinioCertificateStoreTest {

    private static final byte[] CONTENT = "СТЕНД: вымышленная справка".getBytes(StandardCharsets.UTF_8);
    private static final StoredFile FILE = new StoredFile("certificates/0b6c/3c2f.jpg", "0".repeat(64),
            CONTENT.length, "image/jpeg");

    private final MinioClient client = mock(MinioClient.class);
    private final MinioCertificateStore store = new MinioCertificateStore(client, "rvl-contracts");

    @Test
    void file_isPutIntoTheBucketUnderItsKeyWithSizeTypeAndContent() throws Exception {
        byte[][] sent = new byte[1][];
        when(client.putObject(any())).thenAnswer(call -> {
            sent[0] = call.<PutObjectArgs>getArgument(0).stream().readAllBytes();
            return null;
        });

        store.store(FILE, () -> new ByteArrayInputStream(CONTENT));

        ArgumentCaptor<PutObjectArgs> put = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(client).putObject(put.capture());
        assertThat(put.getValue().bucket()).isEqualTo("rvl-contracts");
        assertThat(put.getValue().object()).isEqualTo(FILE.key());
        assertThat(put.getValue().objectSize()).isEqualTo(CONTENT.length);
        assertThat(put.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(sent[0]).isEqualTo(CONTENT);
    }

    @Test
    void contentStream_isClosedAfterThePut() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream content = new ByteArrayInputStream(CONTENT) {
            @Override
            public void close() {
                closed.set(true);
            }
        };

        store.store(FILE, () -> content);

        assertThat(closed).isTrue();
    }

    @Test
    void refusalOfTheStorage_isStorageUnavailable() throws Exception {
        ErrorResponseException refused = new ErrorResponseException(
                new ErrorResponse("NoSuchBucket", "The specified bucket does not exist", "rvl-contracts", null, null,
                        null, null), null, null);
        when(client.putObject(any())).thenThrow(refused);

        assertThatThrownBy(() -> store.store(FILE, () -> new ByteArrayInputStream(CONTENT)))
                .isInstanceOf(StorageUnavailableException.class).hasCause(refused)
                .hasMessage("хранилище справок недоступно: ErrorResponseException");
    }

    @Test
    void networkFailure_isStorageUnavailable() throws Exception {
        IOException refused = new IOException("Connection refused");
        when(client.putObject(any())).thenThrow(refused);

        assertThatThrownBy(() -> store.store(FILE, () -> new ByteArrayInputStream(CONTENT)))
                .isInstanceOf(StorageUnavailableException.class).hasCause(refused);
    }

    @Test
    void serverErrorAndSigningFailure_areStorageUnavailable() throws Exception {
        when(client.putObject(any())).thenThrow(new ServerException("503", 503, "trace"))
                .thenThrow(new InvalidKeyException("key"));

        assertThatThrownBy(() -> store.store(FILE, () -> new ByteArrayInputStream(CONTENT)))
                .isInstanceOf(StorageUnavailableException.class).hasCauseInstanceOf(ServerException.class);
        assertThatThrownBy(() -> store.store(FILE, () -> new ByteArrayInputStream(CONTENT)))
                .isInstanceOf(StorageUnavailableException.class).hasCauseInstanceOf(InvalidKeyException.class);
    }

    @Test
    void unreadableContent_isAFailureOfTheUploadAndNotOfTheStorage() {
        ContentSource unreadable = () -> {
            throw new IOException("поток закрыт");
        };

        assertThatThrownBy(() -> store.store(FILE, unreadable)).isInstanceOf(UncheckedIOException.class)
                .hasMessage("файл справки не прочитан");

        verifyNoInteractions(client);
    }

    @Test
    void failureTheClientWrapsUnchecked_isStorageUnavailable() throws Exception {
        RuntimeException interrupted = new RuntimeException(new InterruptedException());
        when(client.putObject(any())).thenThrow(interrupted);

        assertThatThrownBy(() -> store.store(FILE, () -> new ByteArrayInputStream(CONTENT)))
                .isInstanceOf(StorageUnavailableException.class).hasCause(interrupted);
    }

    @Test
    void verifyPresent_asksTheStorageForTheObjectUnderTheKeyInTheBucket() throws Exception {
        store.verifyPresent(FILE.key());

        ArgumentCaptor<StatObjectArgs> stat = ArgumentCaptor.forClass(StatObjectArgs.class);
        verify(client).statObject(stat.capture());
        assertThat(stat.getValue().bucket()).isEqualTo("rvl-contracts");
        assertThat(stat.getValue().object()).isEqualTo(FILE.key());
    }

    @Test
    void verifyPresent_ofAMissingObject_isACertificateMissing() throws Exception {
        ErrorResponseException missing = new ErrorResponseException(
                new ErrorResponse("NoSuchKey", "Object does not exist", "rvl-contracts", FILE.key(), null, null, null),
                null, null);
        when(client.statObject(any())).thenThrow(missing);

        assertThatThrownBy(() -> store.verifyPresent(FILE.key())).isInstanceOf(CertificateMissingException.class)
                .hasCause(missing).hasMessage("объекта справки нет в хранилище");
    }

    @Test
    void verifyPresent_refusedForAnotherReason_isStorageUnavailable() throws Exception {
        ErrorResponseException refused = new ErrorResponseException(
                new ErrorResponse("AccessDenied", "Access Denied.", "rvl-contracts", FILE.key(), null, null, null),
                null, null);
        when(client.statObject(any())).thenThrow(refused);

        assertThatThrownBy(() -> store.verifyPresent(FILE.key())).isInstanceOf(StorageUnavailableException.class)
                .hasCause(refused);
    }

    @Test
    void verifyPresent_thatFailsOnTheNetworkOrInTheClient_isStorageUnavailable() throws Exception {
        IOException refused = new IOException("Connection refused");
        when(client.statObject(any())).thenThrow(refused).thenThrow(new ServerException("503", 503, "trace"))
                .thenThrow(new RuntimeException(new InterruptedException()));

        assertThatThrownBy(() -> store.verifyPresent(FILE.key())).isInstanceOf(StorageUnavailableException.class)
                .hasCause(refused);
        assertThatThrownBy(() -> store.verifyPresent(FILE.key())).isInstanceOf(StorageUnavailableException.class)
                .hasCauseInstanceOf(ServerException.class);
        assertThatThrownBy(() -> store.verifyPresent(FILE.key())).isInstanceOf(StorageUnavailableException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void streamThatCannotBeClosed_isAFailureOfTheUpload() {
        InputStream content = new ByteArrayInputStream(CONTENT) {
            @Override
            public void close() throws IOException {
                throw new IOException("не закрыт");
            }
        };

        assertThatThrownBy(() -> store.store(FILE, () -> content)).isInstanceOf(UncheckedIOException.class)
                .hasMessage("файл справки не закрыт после записи");
    }
}
