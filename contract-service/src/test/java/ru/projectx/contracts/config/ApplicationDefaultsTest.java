package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ApplicationDefaultsTest {

    private static PropertySource<?> application;

    @BeforeAll
    static void loadApplicationYaml() throws IOException {
        application = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml")).getFirst();
    }

    @Test
    void defaultDatabaseUrl_hidesServerErrorDetail() {
        assertThat(application.getProperty("spring.datasource.url")).asString()
                .startsWith("${DB_URL:jdbc:postgresql://")
                .endsWith("?logServerErrorDetail=false}");
    }

    @Test
    void localDefaults_doNotCollideWithTheReferenceServiceContour() {
        assertThat(application.getProperty("spring.datasource.url")).asString().contains("localhost:5433/");
        assertThat(application.getProperty("projectx.brokers.internal.connection.port")).asString()
                .isEqualTo("${RABBITMQ_INTERNAL_PORT:5674}");
        assertThat(application.getProperty("projectx.brokers.onec.connection.port")).asString()
                .isEqualTo("${RABBITMQ_ONEC_PORT:5675}");
        assertThat(application.getProperty("projectx.reference.url")).asString()
                .isEqualTo("${REFERENCE_URL:}");
    }

    @Test
    void referenceClientDefaults_matchTheReferenceServiceContract() {
        assertThat(application.getProperty("projectx.reference.connect-timeout")).asString()
                .isEqualTo("${REFERENCE_CONNECT_TIMEOUT:2s}");
        assertThat(application.getProperty("projectx.reference.read-timeout")).asString()
                .isEqualTo("${REFERENCE_READ_TIMEOUT:7s}");
        assertThat(application.getProperty("projectx.reference.retry.max-retries")).asString()
                .isEqualTo("${REFERENCE_MAX_RETRIES:3}");
        assertThat(application.getProperty("projectx.reference.retry.delay")).asString()
                .isEqualTo("${REFERENCE_RETRY_DELAY:1s}");
    }

    @Test
    void storageDefaults_leaveAddressBucketAndKeysToTheEnvironment() {
        assertThat(application.getProperty("projectx.storage.endpoint")).asString().isEqualTo("${S3_ENDPOINT:}");
        assertThat(application.getProperty("projectx.storage.bucket")).asString().isEqualTo("${S3_BUCKET:}");
        assertThat(application.getProperty("projectx.storage.access-key")).asString().isEqualTo("${S3_ACCESS_KEY:}");
        assertThat(application.getProperty("projectx.storage.secret-key")).asString().isEqualTo("${S3_SECRET_KEY:}");
        assertThat(application.getProperty("projectx.storage.ssl-bundle")).asString().isEqualTo("${S3_SSL_BUNDLE:}");
        assertThat(application.getProperty("projectx.storage.region")).asString().isEqualTo("${S3_REGION:us-east-1}");
        assertThat(application.getProperty("projectx.storage.base-path")).asString()
                .isEqualTo("${S3_BASE_PATH:certificates}");
    }

    @Test
    void storageTimeouts_endTheCallBeforeTheRequestsServiceGivesUp() {
        assertThat(application.getProperty("projectx.storage.timeouts.connect")).asString()
                .isEqualTo("${S3_CONNECT_TIMEOUT:2s}");
        assertThat(application.getProperty("projectx.storage.timeouts.read")).asString()
                .isEqualTo("${S3_READ_TIMEOUT:10s}");
        assertThat(application.getProperty("projectx.storage.timeouts.write")).asString()
                .isEqualTo("${S3_WRITE_TIMEOUT:10s}");
        assertThat(application.getProperty("projectx.storage.timeouts.call")).asString()
                .isEqualTo("${S3_CALL_TIMEOUT:20s}");
    }

    @Test
    void attachmentDefaults_acceptJpegPngAndPdfUpToFiveMebibytes() {
        assertThat(application.getProperty("projectx.attachments.max-file-size")).asString()
                .isEqualTo("${ATTACHMENT_MAX_FILE_SIZE:5MB}");
        assertThat(application.getProperty("projectx.attachments.content-types[image/jpeg]")).isEqualTo("jpg");
        assertThat(application.getProperty("projectx.attachments.content-types[image/png]")).isEqualTo("png");
        assertThat(application.getProperty("projectx.attachments.content-types[application/pdf]")).isEqualTo("pdf");
        assertThat(new Binder(ConfigurationPropertySources.from(application))
                .bind("projectx.attachments.content-types", Bindable.mapOf(String.class, String.class)).get())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("image/jpeg", "jpg", "image/png", "png", "application/pdf", "pdf"));
    }

    @Test
    void multipartLimits_leaveRoomForTheAttachmentRuleAndKeepFilesInMemory() {
        assertThat(application.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("10MB");
        assertThat(application.getProperty("spring.servlet.multipart.max-request-size")).isEqualTo("10MB");
        assertThat(application.getProperty("spring.servlet.multipart.file-size-threshold")).isEqualTo("10MB");
        assertThat(application.getProperty("server.tomcat.max-swallow-size")).isEqualTo("20MB");
    }

    @Test
    void outboxDefaults_publishEveryFiveSecondsWithConfirmTimeoutLadderAndRetention() {
        assertThat(application.getProperty("projectx.outbox.publishing.interval")).asString()
                .isEqualTo("${OUTBOX_PUBLISHING_INTERVAL:5s}");
        assertThat(application.getProperty("projectx.outbox.publishing.batch-size")).asString()
                .isEqualTo("${OUTBOX_PUBLISHING_BATCH_SIZE:100}");
        assertThat(application.getProperty("projectx.outbox.confirm-timeout")).asString()
                .isEqualTo("${OUTBOX_CONFIRM_TIMEOUT:10s}");
        assertThat(application.getProperty("projectx.outbox.retry.backoff")).asString()
                .isEqualTo("${OUTBOX_RETRY_BACKOFF:1m,5m,15m}");
        assertThat(application.getProperty("projectx.outbox.retention.payload")).asString()
                .isEqualTo("${OUTBOX_PAYLOAD_RETENTION:30d}");
        assertThat(application.getProperty("projectx.outbox.retention.interval")).asString()
                .isEqualTo("${OUTBOX_RETENTION_INTERVAL:1h}");
        assertThat(application.getProperty("projectx.outbox.retention.batch-size")).asString()
                .isEqualTo("${OUTBOX_RETENTION_BATCH_SIZE:500}");
    }

    @Test
    void lmPolicyRequestDefaults_prepareEveryTenSecondsInBatchesOfAHundred() {
        assertThat(application.getProperty("projectx.lm-policy-requests.interval")).asString()
                .isEqualTo("${LM_POLICY_REQUEST_INTERVAL:10s}");
        assertThat(application.getProperty("projectx.lm-policy-requests.batch-size")).asString()
                .isEqualTo("${LM_POLICY_REQUEST_BATCH_SIZE:100}");
    }

    @Test
    void lmPolicyAwaitingDefaults_refreshEveryMinuteAndCountOverdueAfterAnHour() {
        assertThat(application.getProperty("projectx.lm-policy-requests.awaiting-interval")).asString()
                .isEqualTo("${LM_POLICY_REQUEST_AWAITING_INTERVAL:1m}");
        assertThat(application.getProperty("projectx.lm-policy-requests.awaiting-threshold")).asString()
                .isEqualTo("${LM_POLICY_REQUEST_AWAITING_THRESHOLD:1h}");
    }

    @Test
    void schedulerPool_hasAThreadPerScheduledPass() {
        assertThat(application.getProperty("spring.task.scheduling.pool.size")).asString().isEqualTo("6");
    }

    @Test
    void jsonFieldNames_areSnakeCaseForTheWholeContour() {
        assertThat(application.getProperty("spring.jackson.property-naming-strategy")).asString()
                .isEqualTo("SNAKE_CASE");
    }

    @Test
    void defaultManagementPort_is8081() {
        assertThat(application.getProperty("management.server.port")).asString().isEqualTo("${MANAGEMENT_PORT:8081}");
    }

    @Test
    void rabbitAutoConfiguration_isExcluded() {
        assertThat(application.getProperty("spring.autoconfigure.exclude[0]")).asString()
                .isEqualTo("org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration");
        assertThat(application.getProperty("management.health.rabbit.enabled")).asString().isEqualTo("false");
    }

    @ParameterizedTest
    @ValueSource(strings = {"internal", "onec"})
    void brokerPassword_hasEmptyDefault(String broker) {
        assertThat(application.getProperty("projectx.brokers." + broker + ".connection.password")).asString()
                .isEqualTo("${RABBITMQ_" + broker.toUpperCase(Locale.ROOT) + "_PASSWORD:}");
    }

    @Test
    void queueNames_matchTheContourContract() {
        assertThat(queueDefault("internal", "task-changes")).isEqualTo("rvl_requests_to_rvl_contracts_task-changes");
        assertThat(queueDefault("internal", "status-changes"))
                .isEqualTo("rvl_contracts_to_rvl_requests_status-changes");
        assertThat(queueDefault("onec", "km-contract-submissions"))
                .isEqualTo("rvl_contracts_to_1c-usk_km-contract-changes");
        assertThat(queueDefault("onec", "km-contract-registrations"))
                .isEqualTo("1c-usk_to_rvl_contracts_km-contract-changes");
        assertThat(queueDefault("onec", "lm-policy-requests")).isEqualTo("rvl_contracts_to_1c-usk_lm-policy-changes");
        assertThat(queueDefault("onec", "lm-policy-issues")).isEqualTo("1c-usk_to_rvl_contracts_lm-policy-changes");
    }

    private static String queueDefault(String broker, String queue) {
        String placeholder = String.valueOf(application.getProperty("projectx.brokers." + broker + ".queues." + queue));
        return placeholder.substring(placeholder.indexOf(':') + 1, placeholder.length() - 1);
    }
}
