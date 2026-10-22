package ru.projectx.contracts.service.integration;

import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.projectx.contracts.config.StorageProperties;

@Configuration(proxyBeanMethods = false)
public class StorageClientConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageClientConfig.class);

    @Bean(destroyMethod = "close")
    public MinioClient storageClient(StorageProperties settings, SslBundles bundles) {
        log.info("справки хранятся в {} в бакете {} под {}, таймауты соединения {}, чтения {}, записи {}, вызова {}",
                settings.endpoint(), settings.bucket(), settings.basePath(), settings.timeouts().connect(),
                settings.timeouts().read(), settings.timeouts().write(), settings.timeouts().call());
        return MinioClient.builder()
                .endpoint(settings.endpoint())
                .region(settings.region())
                .credentials(settings.accessKey(), settings.secretKey())
                .httpClient(StorageHttpClients.of(settings, bundles), true)
                .build();
    }

    @Bean
    public MinioCertificateStore certificateStore(MinioClient storageClient, StorageProperties settings) {
        return new MinioCertificateStore(storageClient, settings.bucket());
    }
}
