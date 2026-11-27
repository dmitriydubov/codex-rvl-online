package ru.projectx.contracts.service.integration.storage;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioAsyncClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.messages.ErrorResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.projectx.contracts.exception.CertificateMissingException;
import ru.projectx.contracts.exception.StorageUnavailableException;

class MinioAttachmentObjectsTest {

    private static final String BUCKET = "contracts";
    private static final String KEY = "private/test-object.pdf";
    private static final byte[] BYTES = {(byte) 0xff, 0, 19};

    private final MinioAsyncClient client = mock(MinioAsyncClient.class);
    private final MinioAttachmentStore store = new MinioAttachmentStore(client, BUCKET);

    @Test
    void open_returnsTheObjectStreamAndTheCallerCanCloseIt() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        var data = new ByteArrayInputStream(BYTES) {
            @Override
            public void close() {
                closed.set(true);
            }
        };
        when(client.getObject(any())).thenReturn(completedFuture(new GetObjectResponse(null, BUCKET, null, KEY, data)));

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
        when(client.removeObject(any())).thenReturn(completedFuture(null));
        store.remove(KEY);

        var args = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client).removeObject(args.capture());
        assertThat(args.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(args.getValue().object()).isEqualTo(KEY);
    }

    @Test
    void missingKeyInAnExistingBucket_isReportedWithoutTheKey() throws Exception {
        when(client.getObject(any())).thenReturn(failedFuture(refused("NoSuchKey")));
        when(client.bucketExists(any())).thenReturn(completedFuture(true));

        assertThatThrownBy(() -> store.open(KEY)).isInstanceOf(CertificateMissingException.class)
                .hasNoCause().hasMessageNotContaining(KEY);
    }

    @Test
    void missingBucketAndInconclusiveBucketChecks_areStorageFailures() throws Exception {
        when(client.getObject(any())).thenReturn(failedFuture(refused("NoSuchBucket")))
                .thenReturn(failedFuture(refused("NoSuchKey")));
        when(client.bucketExists(any())).thenReturn(completedFuture(false)).thenReturn(failedFuture(new IOException(KEY)));

        assertReadUnavailable();
        assertReadUnavailable();
        assertReadUnavailable();
    }

    @Test
    void missingBucketIsAlreadyAStorageFailureAndDoesNotCallHead() throws Exception {
        when(client.getObject(any())).thenReturn(failedFuture(refused("NoSuchBucket")));

        assertReadUnavailable();

        verify(client, never()).bucketExists(any());
    }

    @Test
    void malformedAndFailedStorageResponses_areReportedWithoutTheirContents() throws Exception {
        when(client.getObject(any())).thenReturn(failedFuture(new InvalidResponseException(404, "", "", KEY)))
                .thenReturn(failedFuture(new ServerException(KEY, 503, KEY)))
                .thenThrow(new IOException(KEY), new IllegalStateException(KEY));

        for (int attempt = 0; attempt < 4; attempt++) {
            assertReadUnavailable();
        }
    }

    @Test
    void deleteRefusalAndNetworkFailure_areReportedWithoutTheKey() throws Exception {
        when(client.removeObject(any())).thenReturn(failedFuture(refused("NoSuchBucket")))
                .thenThrow(new IOException(KEY), new IllegalStateException(KEY));

        for (int attempt = 0; attempt < 3; attempt++) {
            assertThatThrownBy(() -> store.remove(KEY)).isInstanceOf(StorageUnavailableException.class)
                    .hasNoCause().hasMessageNotContaining(KEY);
        }
    }

    @Test
    void interruptedDownloadRestoresTheFlagAndClosesTheLateResponse() throws Exception {
        var pending = new CompletableFuture<GetObjectResponse>();
        when(client.getObject(any())).thenReturn(pending);

        assertInterrupted(() -> store.open(KEY));

        var response = mock(GetObjectResponse.class);
        pending.complete(response);
        verify(response).close();
    }

    @Test
    void interruptedDeletionAndBucketCheckRestoreTheFlag() throws Exception {
        when(client.removeObject(any())).thenReturn(new CompletableFuture<>());
        when(client.getObject(any())).thenReturn(failedFuture(refused("NoSuchKey")));
        when(client.bucketExists(any())).thenReturn(new CompletableFuture<>());

        assertInterrupted(() -> store.remove(KEY));
        assertInterrupted(() -> store.open(KEY));
    }

    private static void assertInterrupted(Runnable call) {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(call::run).isInstanceOf(StorageUnavailableException.class).hasNoCause();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private void assertReadUnavailable() {
        assertThatThrownBy(() -> store.open(KEY)).isInstanceOf(StorageUnavailableException.class)
                .hasNoCause().hasMessageNotContaining(KEY);
    }

    private static ErrorResponseException refused(String code) {
        var request = new Request.Builder().url("http://storage.test/" + BUCKET + "/" + KEY).build();
        var response = new Response.Builder().request(request).code(404).message(code).protocol(Protocol.HTTP_1_1).build();
        return new ErrorResponseException(new ErrorResponse(code, KEY, BUCKET, KEY, null, null, null), response, null);
    }
}
