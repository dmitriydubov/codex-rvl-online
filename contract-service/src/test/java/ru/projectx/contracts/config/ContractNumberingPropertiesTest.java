package ru.projectx.contracts.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ContractNumberingPropertiesTest {

    private final ApplicationContextRunner contexts = new ApplicationContextRunner()
            .withUserConfiguration(Numbering.class);

    @Test
    void zone_isBound() {
        contexts.withPropertyValues("projectx.contracts.numbering.zone=Europe/Moscow").run(context ->
                assertThat(context.getBean(ContractNumberingProperties.class).zone()).isEqualTo(ZoneId.of("Europe/Moscow")));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ContractNumberingProperties.class)
    static class Numbering {
    }
}
