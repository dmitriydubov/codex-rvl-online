package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Настройки задач по расписанию: умолчания, привязка из свойств и значения, которые не пускают
 * сервис в работу.
 *
 * <p>Привязка настоящая, Boot-овская, но без приложения, базы и брокера: контекст из одной
 * записи. Этого достаточно, потому что проверка идёт на старте — неверное значение обязано
 * уронить запуск, а не всплыть потом тиком не в тот момент.
 */
class ImportPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ImportPropertiesBinding.class);

    @Test
    void initialDelay_defaultsToThirtySeconds() {
        context.run(started -> assertThat(started.getBean(ImportProperties.class).initialDelay())
                .isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void schedules_haveTheirDefaults() {
        context.run(started -> {
            ImportProperties properties = started.getBean(ImportProperties.class);
            assertThat(properties.pollInterval()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.retentionPollInterval()).isEqualTo(Duration.ofHours(1));
            assertThat(properties.stuckTransferPollInterval()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.stuckTransferReportLimit()).isEqualTo(20);
            assertThat(properties.retentionBatchSize()).isEqualTo(500);
        });
    }

    /** Каждый интервал читается из своего свойства: опечатка в имени молча оставила бы умолчание. */
    @Test
    void schedules_areBoundFromTheirProperties() {
        context.withPropertyValues("projectx.import.poll-interval=11s", "projectx.import.retention-poll-interval=2h",
                        "projectx.import.stuck-transfer-poll-interval=16m",
                        "projectx.import.stuck-transfer-report-limit=5")
                .run(started -> {
                    ImportProperties properties = started.getBean(ImportProperties.class);
                    assertThat(properties.pollInterval()).isEqualTo(Duration.ofSeconds(11));
                    assertThat(properties.retentionPollInterval()).isEqualTo(Duration.ofHours(2));
                    assertThat(properties.stuckTransferPollInterval()).isEqualTo(Duration.ofMinutes(16));
                    assertThat(properties.stuckTransferReportLimit()).isEqualTo(5);
                });
    }

    /**
     * Пауза между проходами обязана быть больше нуля: нулевую пул планировщика отвергает исключением при
     * регистрации задачи, не называя свойства. Проверка при привязке называет его.
     */
    @ParameterizedTest
    @CsvSource({"poll-interval,pollInterval", "retention-poll-interval,retentionPollInterval",
            "stuck-transfer-poll-interval,stuckTransferPollInterval"})
    void zeroInterval_failsStartup(String property, String component) {
        context.withPropertyValues("projectx.import." + property + "=0s")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining(component));
    }

    @Test
    void zeroStuckTransferThreshold_failsStartup() {
        context.withPropertyValues("projectx.import.stuck-transfer-threshold=0s")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("stuckTransferThreshold"));
    }

    /**
     * Срок хранения тела меньше суток не пускает сервис в работу: со сроком около нуля стирались бы тела ещё
     * не разобранных полисов, и разобрать их стало бы нечем. Сутки ровно — уже допустимы.
     */
    @Test
    void payloadTtlBelowOneDay_failsStartup() {
        context.withPropertyValues("projectx.import.payload-ttl=23h")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("payloadTtl"));
    }

    /** Сутки — наименьший срок хранения; срок ожидания родителя тогда должен быть короче суток. */
    @Test
    void payloadTtlOfOneDay_isAccepted() {
        context.withPropertyValues("projectx.import.payload-ttl=1d", "projectx.import.parent-wait-timeout=12h")
                .run(started -> assertThat(started.getBean(ImportProperties.class).payloadTtl())
                        .isEqualTo(Duration.ofDays(1)));
    }

    @Test
    void zeroReportLimit_failsStartup() {
        context.withPropertyValues("projectx.import.stuck-transfer-report-limit=0")
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("stuckTransferReportLimit"));
    }

    /**
     * Явно пустое значение, например заданная, но пустая переменная окружения, — это умолчание, а не
     * нулевая задержка: иначе забытое значение в манифесте молча вернуло бы первый проход на старт.
     */
    @Test
    void emptyInitialDelay_fallsBackToDefault() {
        context.withPropertyValues("projectx.import.initial-delay=")
                .run(started -> assertThat(started.getBean(ImportProperties.class).initialDelay())
                        .isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void initialDelay_isBoundFromProperty() {
        context.withPropertyValues("projectx.import.initial-delay=2m")
                .run(started -> assertThat(started.getBean(ImportProperties.class).initialDelay())
                        .isEqualTo(Duration.ofMinutes(2)));
    }

    /** Ноль — осознанное «без задержки», как было до неё: это значение, а не ошибка. */
    @Test
    void zeroInitialDelay_isAccepted() {
        context.withPropertyValues("projectx.import.initial-delay=0s")
                .run(started -> assertThat(started.getBean(ImportProperties.class).initialDelay())
                        .isZero());
    }

    /**
     * Отрицательную задержку планировщик Spring молча принимает за нулевую, и первый проход
     * случается сразу на старте — ровно то, от чего задержка защищает. Поэтому такое значение
     * не должно пускать сервис в работу вовсе. Наносекунда ниже нуля — само место границы:
     * ноль разрешён, всё меньше него — нет, и граница, сдвинутая на миллисекунду вниз, здесь видна.
     */
    @ParameterizedTest
    @ValueSource(strings = {"-1s", "-1ns"})
    void negativeInitialDelay_failsStartup(String initialDelay) {
        context.withPropertyValues("projectx.import.initial-delay=" + initialDelay)
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining("initialDelay"));
    }

    @Test
    void unreadableInitialDelay_failsStartup() {
        context.withPropertyValues("projectx.import.initial-delay=soon")
                .run(started -> assertThat(started).getFailure()
                        .cause().hasMessageContaining("projectx.import.initial-delay"));
    }

    @Test
    void parentWait_defaultsToADayAndTwentyParents() {
        context.run(started -> {
            ImportProperties properties = started.getBean(ImportProperties.class);
            assertThat(properties.parentWaitTimeout()).isEqualTo(Duration.ofHours(24));
            assertThat(properties.parentWaitReportLimit()).isEqualTo(20);
        });
    }

    @Test
    void parentWait_isBoundFromItsProperties() {
        context.withPropertyValues("projectx.import.parent-wait-timeout=36h",
                        "projectx.import.parent-wait-report-limit=5")
                .run(started -> {
                    ImportProperties properties = started.getBean(ImportProperties.class);
                    assertThat(properties.parentWaitTimeout()).isEqualTo(Duration.ofHours(36));
                    assertThat(properties.parentWaitReportLimit()).isEqualTo(5);
                });
    }

    @ParameterizedTest
    @CsvSource({"parent-wait-timeout=0s,parentWaitTimeout", "parent-wait-report-limit=0,parentWaitReportLimit"})
    void zeroParentWaitSetting_failsStartup(String setting, String component) {
        context.withPropertyValues("projectx.import." + setting)
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining(component));
    }

    /**
     * Тело ожидающего сообщения не стирается, поэтому срок ожидания не дольше срока хранения тела не пускает сервис в
     * работу, и ошибка называет оба свойства. Срок на волос короче срока хранения допустим.
     */
    @ParameterizedTest
    @CsvSource({"30d,30d,false", "31d,30d,false", "2d,1d,false", "29d,30d,true", "PT719H59M59S,30d,true"})
    void parentWaitAgainstPayloadTtl_decidesTheStartup(String timeout, String payloadTtl, boolean starts) {
        context.withPropertyValues("projectx.import.parent-wait-timeout=" + timeout,
                        "projectx.import.payload-ttl=" + payloadTtl)
                .run(started -> {
                    if (starts) {
                        assertThat(started).hasNotFailed();
                        return;
                    }
                    assertThat(started).getFailure()
                            .hasRootCauseInstanceOf(BindValidationException.class)
                            .rootCause()
                            .hasMessageContaining("parent-wait-timeout")
                            .hasMessageContaining("payload-ttl");
                });
    }

    @EnableConfigurationProperties(ImportProperties.class)
    static class ImportPropertiesBinding {
    }
}
