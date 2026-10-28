package ru.projectx.contracts.config.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class StoragePropertiesBindingTest {

    private static final String PREFIX = "projectx.storage";

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(Storage.class)
            .withPropertyValues(
                    PREFIX + ".endpoint=https://s3.contour.example:9000",
                    PREFIX + ".region=ru-central-1",
                    PREFIX + ".bucket=rvl-contracts",
                    PREFIX + ".base-path=certificates",
                    PREFIX + ".inn=1111111111",
                    PREFIX + ".kpp=222222222",
                    PREFIX + ".access-key=contracts-access",
                    PREFIX + ".secret-key=contracts-secret",
                    PREFIX + ".timeouts.connect=2s",
                    PREFIX + ".timeouts.read=10s",
                    PREFIX + ".timeouts.write=10s",
                    PREFIX + ".timeouts.call=20s");

    @Test
    void completeConfiguration_bindsWithoutCertificateBundle() {
        contexts.run(context -> {
            StorageProperties storage = context.getBean(StorageProperties.class);

            assertThat(storage.endpoint()).isEqualTo("https://s3.contour.example:9000");
            assertThat(storage.region()).isEqualTo("ru-central-1");
            assertThat(storage.bucket()).isEqualTo("rvl-contracts");
            assertThat(storage.basePath()).isEqualTo("certificates");
            assertThat(storage.inn()).isEqualTo("1111111111");
            assertThat(storage.kpp()).isEqualTo("222222222");
            assertThat(storage.accessKey()).isEqualTo("contracts-access");
            assertThat(storage.secretKey()).isEqualTo("contracts-secret");
            assertThat(storage.timeouts()).isEqualTo(new StorageProperties.Timeouts(Duration.ofSeconds(2),
                    Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofSeconds(20)));
            assertThat(storage.sslBundle()).isNull();
        });
    }

    @Test
    void certificateBundle_isTakenByName() {
        contexts.withPropertyValues(PREFIX + ".ssl-bundle=storage").run(context ->
                assertThat(context.getBean(StorageProperties.class).sslBundle()).isEqualTo("storage"));
    }

    @ParameterizedTest
    @CsvSource({
        "endpoint, '', endpoint",
        "endpoint, s3.contour.example:9000, endpoint",
        "endpoint, https://s3.contour.example/, endpoint",
        "region, '', region",
        "bucket, '', bucket",
        "base-path, '', basePath",
        "base-path, /certificates, basePath",
        "base-path, certificates/, basePath",
        "inn, '', inn",
        "inn, 111111111, inn",
        "inn, 11111111111, inn",
        "inn, 111111111a, inn",
        "kpp, '', kpp",
        "kpp, 22222222, kpp",
        "kpp, 2222222222, kpp",
        "kpp, 22222222a, kpp",
        "access-key, '', accessKey",
        "secret-key, '', secretKey",
        "timeouts.connect, 0s, connect",
        "timeouts.read, 0ms, read",
        "timeouts.write, 0s, write",
        "timeouts.call, 0s, call"})
    void invalidValue_failsStartupNamingTheField(String property, String value, String field) {
        contexts.withPropertyValues(PREFIX + "." + property + "=" + value).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining(field);
        });
    }

    @Test
    void validationFailure_doesNotRevealTheSecretKey() {
        contexts.withPropertyValues(PREFIX + ".bucket=", PREFIX + ".secret-key=contracts-secret").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().message().doesNotContain("contracts-secret");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(StorageProperties.class)
    static class Storage {
    }
}
