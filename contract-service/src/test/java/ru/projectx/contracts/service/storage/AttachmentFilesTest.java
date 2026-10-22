package ru.projectx.contracts.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;
import ru.projectx.contracts.config.AttachmentProperties;
import ru.projectx.contracts.exception.AttachmentDefect;
import ru.projectx.contracts.exception.AttachmentRejectedException;

class AttachmentFilesTest {

    private static final long LIMIT = 5_242_880;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AttachmentFiles files = new AttachmentFiles(
            new AttachmentProperties(DataSize.ofBytes(LIMIT), Map.of("image/jpeg", "jpg", "image/png", "png")),
            new AttachmentMetrics(registry));

    @ParameterizedTest
    @CsvSource({
        "image/jpeg, image/jpeg, jpg",
        "image/png, image/png, png",
        "IMAGE/PNG, image/png, png",
        "' image/jpeg ; name=x', image/jpeg, jpg"})
    void configuredType_isAcceptedWithItsExtension(String declared, String type, String extension) {
        assertThat(files.accept(Uploads.sized(declared, 76)))
                .isEqualTo(new AttachmentFiles.AcceptedFile(type, extension));
        assertThat(registry.find("contracts.attachments.rejected").counter()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/pdf", "image/gif", "", "image/jpeg2", "jpeg"})
    void otherType_isRejectedAsContentTypeAndCounted(String declared) {
        assertThatThrownBy(() -> files.accept(Uploads.sized(declared, 76)))
                .isInstanceOfSatisfying(AttachmentRejectedException.class,
                        rejected -> assertThat(rejected.defect()).isEqualTo(AttachmentDefect.CONTENT_TYPE));
        assertThat(registry.get("contracts.attachments.rejected").tag("reason", "content_type").counter().count())
                .isEqualTo(1);
    }

    @Test
    void typeStartingWithParameters_hasNoMediaTypeEvenWhenConfiguredLiterally() {
        AttachmentFiles literal = new AttachmentFiles(
                new AttachmentProperties(DataSize.ofBytes(LIMIT), Map.of(";name=x", "jpg")),
                new AttachmentMetrics(registry));

        assertThatThrownBy(() -> literal.accept(Uploads.sized(";name=x", 76)))
                .isInstanceOfSatisfying(AttachmentRejectedException.class,
                        rejected -> assertThat(rejected.defect()).isEqualTo(AttachmentDefect.CONTENT_TYPE));
    }

    @Test
    void missingType_isRejectedAsContentType() {
        assertThatThrownBy(() -> files.accept(Uploads.sized(null, 76)))
                .isInstanceOfSatisfying(AttachmentRejectedException.class,
                        rejected -> assertThat(rejected.defect()).isEqualTo(AttachmentDefect.CONTENT_TYPE));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, LIMIT + 1})
    void emptyOrOverLimitFile_isRejectedAsSizeAndCounted(long size) {
        assertThatThrownBy(() -> files.accept(Uploads.sized("image/png", size)))
                .isInstanceOfSatisfying(AttachmentRejectedException.class,
                        rejected -> assertThat(rejected.defect()).isEqualTo(AttachmentDefect.SIZE));
        assertThat(registry.get("contracts.attachments.rejected").tag("reason", "size").counter().count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, LIMIT})
    void fileFromOneByteUpToTheLimit_isAccepted(long size) {
        assertThat(files.accept(Uploads.sized("image/png", size)).extension()).isEqualTo("png");
    }

    @Test
    void wrongTypeAndSize_isRejectedForTheTypeFirst() {
        assertThatThrownBy(() -> files.accept(Uploads.sized("application/pdf", 0)))
                .isInstanceOfSatisfying(AttachmentRejectedException.class,
                        rejected -> assertThat(rejected.defect()).isEqualTo(AttachmentDefect.CONTENT_TYPE));
    }
}
