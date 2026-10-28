package ru.projectx.contracts.service.integration;

import java.util.Arrays;
import java.util.Optional;
import javax.net.ssl.X509TrustManager;
import io.minio.MinioClient;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import ru.projectx.contracts.config.storage.StorageProperties;

final class StorageHttpClients {

    private static final Logger log = LoggerFactory.getLogger(StorageHttpClients.class);

    private StorageHttpClients() {
    }

    static MinioClient minio(StorageProperties settings, SslBundles bundles) {
        StorageProperties.Timeouts timeouts = settings.timeouts();
        log.info("справки хранятся в {} в бакете {} под {}, таймауты соединения {}, чтения {}, записи {}, вызова {}",
                settings.endpoint(), settings.bucket(), settings.basePath(), timeouts.connect(), timeouts.read(),
                timeouts.write(), timeouts.call());
        return MinioClient.builder()
                .endpoint(settings.endpoint())
                .region(settings.region())
                .credentials(settings.accessKey(), settings.secretKey())
                .httpClient(of(settings, bundles), true)
                .build();
    }

    static OkHttpClient of(StorageProperties settings, SslBundles bundles) {
        StorageProperties.Timeouts timeouts = settings.timeouts();
        OkHttpClient.Builder client = new OkHttpClient.Builder()
                .connectTimeout(timeouts.connect())
                .readTimeout(timeouts.read())
                .writeTimeout(timeouts.write())
                .callTimeout(timeouts.call());
        certificateBundle(settings).map(bundles::getBundle).ifPresent(bundle -> trust(client, bundle));
        return client.build();
    }

    private static Optional<String> certificateBundle(StorageProperties settings) {
        return Optional.ofNullable(settings.sslBundle()).filter(name -> !name.isBlank());
    }

    private static void trust(OkHttpClient.Builder client, SslBundle bundle) {
        X509TrustManager trustManager = x509(bundle).orElseThrow(
                () -> new IllegalStateException("в наборе сертификатов хранилища нет доверенных сертификатов X.509"));
        client.sslSocketFactory(bundle.createSslContext().getSocketFactory(), trustManager);
    }

    private static Optional<X509TrustManager> x509(SslBundle bundle) {
        return Arrays.stream(bundle.getManagers().getTrustManagers())
                .filter(X509TrustManager.class::isInstance)
                .map(X509TrustManager.class::cast)
                .findFirst();
    }
}
