package ru.projectx.contracts.service.integration.storage;

import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.projectx.contracts.config.storage.StorageProperties;

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

    @Bean(destroyMethod = "close")
    public MinioAsyncClient attachmentStorageClient(StorageProperties settings, SslBundles bundles) {
        return StorageHttpClients.minioAsync(settings, bundles);
    }

    @Bean
    public MinioAttachmentStore attachmentStore(MinioAsyncClient attachmentStorageClient, StorageProperties settings) {
        return new MinioAttachmentStore(attachmentStorageClient, settings.bucket());
    }
}
