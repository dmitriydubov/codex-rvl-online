package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import ru.projectx.contracts.config.StorageProperties;

class ObjectKeysTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    private final ObjectKeys keys = new ObjectKeys(new StorageProperties("http://storage.test:9000", "us-east-1",
            "contracts", "rvl/certificates", "access", "secret", null,
            new StorageProperties.Timeouts(TIMEOUT, TIMEOUT, TIMEOUT, TIMEOUT)));

    @Test
    void key_isBasePathThenUniqueFolderThenAttachmentWithExtension() {
        assertThat(keys.next(Uploads.ATTACHMENT, "jpg")).matches(
                "rvl/certificates/[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/"
                        + Uploads.ATTACHMENT + "\\.jpg");
    }

    @Test
    void everyKey_getsItsOwnFolder() {
        assertThat(keys.next(Uploads.ATTACHMENT, "png")).isNotEqualTo(keys.next(Uploads.ATTACHMENT, "png"));
    }
}
