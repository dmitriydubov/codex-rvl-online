package ru.projectx.contracts.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundles;
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

    private static String[] subjects(OkHttpClient client) {
        return Arrays.stream(client.x509TrustManager().getAcceptedIssuers())
                .map(X509Certificate::getSubjectX500Principal).map(Object::toString).toArray(String[]::new);
    }

    private static StorageProperties settings(String bundle) {
        return new StorageProperties("https://s3.contour.example:9000", "us-east-1", "rvl-contracts", "certificates",
                "access", "secret", bundle, TIMEOUTS);
    }
}
