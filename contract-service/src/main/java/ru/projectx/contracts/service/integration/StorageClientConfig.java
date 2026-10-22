package ru.projectx.contracts.service.integration;

import io.minio.MinioClient;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.projectx.contracts.config.StorageProperties;

@Configuration(proxyBeanMethods = false)
public class StorageClientConfig {

    @Bean(destroyMethod = "close")
    public MinioClient storageClient(StorageProperties settings, SslBundles bundles) {
        return StorageHttpClients.minio(settings, bundles);
    }

    @Bean
    public MinioCertificateStore certificateStore(MinioClient storageClient, StorageProperties settings) {
        return new MinioCertificateStore(storageClient, settings.bucket());
    }
}
