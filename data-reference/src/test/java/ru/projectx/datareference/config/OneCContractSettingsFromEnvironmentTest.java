package ru.projectx.datareference.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class OneCContractSettingsFromEnvironmentTest {

    private static final String DMS_PRODUCT_VARIABLE = "PROJECTX_ONEC_CONTRACT_DMSPRODUCTTYPE";
    private static final String INSURER_ROLE_VARIABLE = "PROJECTX_ONEC_CONTRACT_INSURERROLE";

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ContractSettingsOnly.class);

    @Test
    void withoutVariables_bothDmsSpellingsAndTheInsurerRoleOfTheSpecificationApply() {
        context.run(started -> {
            OneCContractProperties contract = started.getBean(OneCContractProperties.class);
            assertThat(contract.dmsProducts()).containsExactly("ЛичноеСтрахование", "Личное страхование");
            assertThat(contract.insurerRole()).isEqualTo("Страхователь");
        });
    }

    @Test
    void standVariable_replacesTheDefaultDmsProducts() {
        withVariable(DMS_PRODUCT_VARIABLE, "ЛичноеСтрахование").run(started -> assertThat(
                started.getBean(OneCContractProperties.class).dmsProducts()).containsExactly("ЛичноеСтрахование"));
    }

    @Test
    void commaSeparatedVariable_givesEveryListedDmsProduct() {
        withVariable(DMS_PRODUCT_VARIABLE, "Личное страхование,ЛичноеСтрахование").run(started -> assertThat(
                started.getBean(OneCContractProperties.class).dmsProducts())
                .containsExactly("Личное страхование", "ЛичноеСтрахование"));
    }

    @Test
    void emptyDmsProductVariable_startsWithoutDmsProducts() {
        withVariable(DMS_PRODUCT_VARIABLE, "").run(started -> {
            assertThat(started).hasNotFailed();
            assertThat(started.getBean(OneCContractProperties.class).dmsProducts()).isEmpty();
        });
    }

    @Test
    void insurerRoleVariable_replacesTheDefaultRole() {
        withVariable(INSURER_ROLE_VARIABLE, "РольСтрахователя").run(started -> assertThat(
                started.getBean(OneCContractProperties.class).insurerRole()).isEqualTo("РольСтрахователя"));
    }

    @Test
    void emptyInsurerRoleVariable_stopsTheStart_namingTheSetting() {
        withVariable(INSURER_ROLE_VARIABLE, "").run(started -> {
            assertThat(started).hasFailed();
            assertThat(fullReport(started.getStartupFailure()))
                    .contains("projectx.onec.contract", "insurerRole")
                    .doesNotContain("Страхователь");
        });
    }

    @Test
    void blankInsurerRoleVariable_stopsTheStart() {
        withVariable(INSURER_ROLE_VARIABLE, "  ").run(started -> assertThat(started).hasFailed());
    }

    private ApplicationContextRunner withVariable(String name, String value) {
        return context.withInitializer(started -> started.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("test-systemEnvironment", Map.of(name, value))));
    }

    private static String fullReport(Throwable failure) {
        StringWriter report = new StringWriter();
        failure.printStackTrace(new PrintWriter(report));
        return report.toString();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OneCContractProperties.class)
    static class ContractSettingsOnly {
    }
}
