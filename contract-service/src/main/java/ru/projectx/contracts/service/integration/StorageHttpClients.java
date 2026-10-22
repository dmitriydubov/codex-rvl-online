package ru.projectx.contracts.service.integration;

import java.util.Arrays;
import java.util.Optional;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundles;
import ru.projectx.contracts.config.StorageProperties;

final class StorageHttpClients {

    private StorageHttpClients() {
    }

    static OkHttpClient of(StorageProperties settings, SslBundles bundles) {
        StorageProperties.Timeouts timeouts = settings.timeouts();
        OkHttpClient.Builder client = new OkHttpClient.Builder()
                .connectTimeout(timeouts.connect())
                .readTimeout(timeouts.read())
                .writeTimeout(timeouts.write())
                .callTimeout(timeouts.call());
        settings.certificateBundle().map(bundles::getBundle).ifPresent(bundle -> trust(client, bundle));
        return client.build();
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
