package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import javax.net.ssl.TrustManager;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import ru.projectx.contracts.config.StorageProperties;

class StorageHttpClientsTest {

    private static final StorageProperties.Timeouts TIMEOUTS = new StorageProperties.Timeouts(
            Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(11), Duration.ofSeconds(20));

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SslAutoConfiguration.class))
            .withPropertyValues("spring.ssl.bundle.pem.storage.truststore.certificate=classpath:storage/test-ca.pem");

    @Test
    void client_hasEveryTimeoutFromSettings() {
        contexts.run(context -> {
            OkHttpClient client = StorageHttpClients.of(settings(null), context.getBean(SslBundles.class));

            assertThat(client.connectTimeoutMillis()).isEqualTo(2_000);
            assertThat(client.readTimeoutMillis()).isEqualTo(10_000);
            assertThat(client.writeTimeoutMillis()).isEqualTo(11_000);
            assertThat(client.callTimeoutMillis()).isEqualTo(20_000);
            assertThat(client.retryOnConnectionFailure()).isTrue();
        });
    }

    @Test
    void withoutBundle_theDefaultTrustOfTheJvmIsKept() {
        contexts.run(context -> {
            OkHttpClient client = StorageHttpClients.of(settings(" "), context.getBean(SslBundles.class));

            assertThat(subjects(client)).doesNotContain("CN=Test Storage CA");
        });
    }

    @Test
    void namedBundle_trustsTheInternalCertificateAuthority() {
        contexts.run(context -> {
            OkHttpClient client = StorageHttpClients.of(settings("storage"), context.getBean(SslBundles.class));

            assertThat(subjects(client)).containsExactly("CN=Test Storage CA");
        });
    }

    @Test
    void unknownBundle_failsStartup() {
        contexts.run(context -> assertThatThrownBy(
                () -> StorageHttpClients.of(settings("missing"), context.getBean(SslBundles.class)))
                .isInstanceOf(NoSuchSslBundleException.class));
    }

    @Test
    void bundleWithoutX509Trust_failsStartup() {
        SslBundle bundle = mock();
        given(bundle.getManagers()).willReturn(SslManagerBundle.from(new TrustManager() {
        }));
        SslBundles bundles = mock();
        given(bundles.getBundle("storage")).willReturn(bundle);

        assertThatThrownBy(() -> StorageHttpClients.of(settings("storage"), bundles))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("X.509");
    }

    @Test
    void minioClient_signsRequestsForTheConfiguredEndpointBucketRegionAndKey() {
        contexts.run(context -> {
            MinioClient client = StorageHttpClients.minio(settings(null), context.getBean(SslBundles.class));

            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.PUT)
                    .bucket("rvl-contracts").object("certificates/a.jpg").build());

            assertThat(url).startsWith("https://s3.contour.example:9000/rvl-contracts/certificates/a.jpg?")
                    .contains("X-Amz-Credential=access%2F").contains("%2Fus-east-1%2Fs3%2F");
        });
    }

    private static String[] subjects(OkHttpClient client) {
        return Arrays.stream(client.x509TrustManager().getAcceptedIssuers())
                .map(X509Certificate::getSubjectX500Principal).map(Object::toString).toArray(String[]::new);
    }

    private static StorageProperties settings(String bundle) {
        return new StorageProperties("https://s3.contour.example:9000", "us-east-1", "rvl-contracts", "certificates",
                "access", "secret", bundle, TIMEOUTS);
    }
}
