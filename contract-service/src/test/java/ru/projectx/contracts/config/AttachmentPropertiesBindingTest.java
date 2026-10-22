package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

class AttachmentPropertiesBindingTest {

    private static final String PREFIX = "projectx.attachments";

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(Attachments.class)
            .withPropertyValues(
                    PREFIX + ".max-file-size=5MB",
                    PREFIX + ".content-types.[image/jpeg]=jpg",
                    PREFIX + ".content-types.[image/png]=png");

    @Test
    void completeConfiguration_bindsSizeInBytesAndExtensionsByType() {
        contexts.run(context -> {
            AttachmentProperties attachments = context.getBean(AttachmentProperties.class);

            assertThat(attachments.maxFileSize()).isEqualTo(DataSize.ofBytes(5_242_880));
            assertThat(attachments.contentTypes()).containsExactlyInAnyOrderEntriesOf(
                    Map.of("image/jpeg", "jpg", "image/png", "png"));
        });
    }

    @ParameterizedTest
    @CsvSource({
        "max-file-size, 0B, maxFileSize",
        "max-file-size, -1B, maxFileSize",
        "content-types.[image/png], '', contentTypes",
        "content-types.[image/png], .png, contentTypes"})
    void invalidValue_failsStartupNamingTheField(String property, String value, String field) {
        contexts.withPropertyValues(PREFIX + "." + property + "=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining(field);
        });
    }

    @Test
    void noContentTypes_failsStartup() {
        new ApplicationContextRunner().withUserConfiguration(Attachments.class)
                .withPropertyValues(PREFIX + ".max-file-size=5MB")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("contentTypes");
                });
    }

    @Test
    void noMaxFileSize_failsStartupAsAMissingField() {
        new ApplicationContextRunner().withUserConfiguration(Attachments.class)
                .withPropertyValues(PREFIX + ".content-types.[image/png]=png")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .isInstanceOf(BindValidationException.class).hasMessageContaining("maxFileSize");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AttachmentProperties.class)
    static class Attachments {
    }
}
