package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.config.storage.StorageProperties;
import ru.projectx.contracts.entity.document.StoredFile;

class CertificateLinksTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    private final CertificateLinks links = new CertificateLinks(new StorageProperties("http://storage.test:9000",
            "us-east-1", "rvl-files", "certificates", "1111111111", "222222222", "access", "secret", null,
            new StorageProperties.Timeouts(TIMEOUT, TIMEOUT, TIMEOUT, TIMEOUT)));

    @Test
    void link_isTheBucketThenTheKeyOfTheRegistryWithoutTheStorageAddress() {
        StoredFile file = new StoredFile("1111111111_222222222/certificates/5b1d/3c2f.jpg", "0".repeat(64), 10,
                "image/jpeg");

        assertThat(links.link(file)).isEqualTo("/rvl-files/1111111111_222222222/certificates/5b1d/3c2f.jpg");
    }

    @Test
    void link_ofACertificateStoredUnderTheEarlierKey_keepsThatKey() {
        StoredFile file = new StoredFile("certificates/5b1d/3c2f.png", "0".repeat(64), 10, "image/png");

        assertThat(links.link(file)).isEqualTo("/rvl-files/certificates/5b1d/3c2f.png");
    }
}
