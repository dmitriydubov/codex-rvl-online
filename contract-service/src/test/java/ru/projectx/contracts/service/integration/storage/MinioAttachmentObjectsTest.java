package ru.projectx.contracts.service.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.messages.ErrorResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;

class MinioAttachmentObjectsTest {

    private static final String BUCKET = "contracts";
    private static final String KEY = "private/test-object.pdf";
    private static final byte[] BYTES = {(byte) 0xff, 0, 19};

    private final MinioClient client = mock(MinioClient.class);
    private final MinioCertificateStore store = new MinioCertificateStore(client, BUCKET);

    @Test
    void open_returnsTheObjectStreamAndTheCallerCanCloseIt() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        var data = new ByteArrayInputStream(BYTES) {
            @Override
            public void close() {
                closed.set(true);
            }
        };
        when(client.getObject(any())).thenReturn(new GetObjectResponse(null, BUCKET, null, KEY, data));

        try (var input = store.open(KEY)) {
            assertThat(input.readAllBytes()).isEqualTo(BYTES);
            assertThat(closed).isFalse();
        }

        assertThat(closed).isTrue();
        var args = ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(client).getObject(args.capture());
        assertThat(args.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(args.getValue().object()).isEqualTo(KEY);
    }

    @Test
    void remove_deletesTheObjectInTheConfiguredBucket() throws Exception {
        store.remove(KEY);

        var args = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client).removeObject(args.capture());
        assertThat(args.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(args.getValue().object()).isEqualTo(KEY);
    }

    @Test
    void missingKeyInAnExistingBucket_isReportedWithoutTheKey() throws Exception {
        when(client.getObject(any())).thenThrow(refused("NoSuchKey"));
        when(client.bucketExists(any())).thenReturn(true);

        assertThatThrownBy(() -> store.open(KEY)).isInstanceOf(CertificateMissingException.class)
                .hasNoCause().hasMessageNotContaining(KEY);
    }

    @Test
    void missingBucketAndInconclusiveBucketChecks_areStorageFailures() throws Exception {
        when(client.getObject(any())).thenThrow(refused("NoSuchBucket"), refused("NoSuchKey"), refused("NoSuchKey"));
        when(client.bucketExists(any())).thenReturn(false).thenThrow(new IOException(KEY));

        assertReadUnavailable();
        assertReadUnavailable();
        assertReadUnavailable();
    }

    @Test
    void malformedAndFailedStorageResponses_areReportedWithoutTheirContents() throws Exception {
        when(client.getObject(any())).thenThrow(new InvalidResponseException(404, "", "", KEY),
                new ServerException(KEY, 503, KEY), new IOException(KEY),
                new RuntimeException(new InterruptedException(KEY)));

        for (int attempt = 0; attempt < 4; attempt++) {
            assertReadUnavailable();
        }
    }

    @Test
    void deleteRefusalAndNetworkFailure_areReportedWithoutTheKey() throws Exception {
        doThrow(refused("NoSuchBucket"), new IOException(KEY), new RuntimeException(KEY))
                .when(client).removeObject(any());

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> store.remove(KEY)).isInstanceOf(StorageUnavailableException.class)
                    .hasNoCause().hasMessageNotContaining(KEY);
        }
    }

    private void assertReadUnavailable() {
        assertThatThrownBy(() -> store.open(KEY)).isInstanceOf(StorageUnavailableException.class)
                .hasNoCause().hasMessageNotContaining(KEY);
    }

    private static ErrorResponseException refused(String code) {
        return new ErrorResponseException(new ErrorResponse(code, KEY, BUCKET, KEY, null, null, null), null, null);
    }
}
