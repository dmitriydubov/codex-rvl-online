package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Настройки ожидания родителя: умолчания, привязка из свойств рядом с остальными настройками разбора и значения,
 * которые не пускают сервис в работу. Нулевой срок отклонял бы каждое ожидающее сообщение на ближайшем проходе, а
 * нулевой предел оставлял бы сводную запись журнала без единого GUID.
 */
class ParentWaitPropertiesTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ParentWaitPropertiesBinding.class);

    @Test
    void defaults_areADayAndTwentyParents() {
        context.run(started -> {
            ParentWaitProperties properties = started.getBean(ParentWaitProperties.class);
            assertThat(properties.parentWaitTimeout()).isEqualTo(Duration.ofHours(24));
            assertThat(properties.parentWaitReportLimit()).isEqualTo(20);
        });
    }

    @Test
    void settings_areBoundFromTheImportProperties() {
        context.withPropertyValues("projectx.import.parent-wait-timeout=36h",
                        "projectx.import.parent-wait-report-limit=5")
                .run(started -> {
                    ParentWaitProperties properties = started.getBean(ParentWaitProperties.class);
                    assertThat(properties.parentWaitTimeout()).isEqualTo(Duration.ofHours(36));
                    assertThat(properties.parentWaitReportLimit()).isEqualTo(5);
                });
    }

    @ParameterizedTest
    @CsvSource({"parent-wait-timeout=0s,parentWaitTimeout", "parent-wait-report-limit=0,parentWaitReportLimit"})
    void zeroSetting_failsStartup(String setting, String component) {
        context.withPropertyValues("projectx.import." + setting)
                .run(started -> assertThat(started).getFailure()
                        .hasRootCauseInstanceOf(BindValidationException.class)
                        .rootCause().hasMessageContaining(component));
    }

    @EnableConfigurationProperties(ParentWaitProperties.class)
    static class ParentWaitPropertiesBinding {
    }
}
