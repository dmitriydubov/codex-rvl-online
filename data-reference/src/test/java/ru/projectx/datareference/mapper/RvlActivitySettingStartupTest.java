package ru.projectx.datareference.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.projectx.datareference.config.OneCContractProperties;
import ru.projectx.datareference.repository.ForeignReferenceRepository;
import ru.projectx.datareference.repository.InsurerRepository;
import ru.projectx.datareference.repository.LpuRepository;

/**
 * GUID вида деятельности «РВЛ» не в канонической форме останавливает старт с понятным текстом, а само значение
 * в отчёт об ошибке не попадает. Контекст маленький, но настройки читаются тем же механизмом Spring Boot.
 */
class RvlActivitySettingStartupTest {

    private static final String SETTING = "projectx.onec.contract.rvl-activity-id";

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(MapperOnly.class);

    @Test
    void nonCanonicalSetting_stopsTheStart_withoutTheValueInTheFailure() {
        context.withPropertyValues(SETTING + "=1-2-3-4-5").run(started -> {
            assertThat(started).hasFailed();
            assertThat(fullReport(started.getStartupFailure()))
                    .contains(SETTING, "8-4-4-4-12")
                    .doesNotContain("1-2-3-4-5");
        });
    }

    /** Нулевой GUID в сообщениях 1С:УСК означает пустое значение, поэтому как настройка он не годится. */
    @Test
    void nilGuidSetting_stopsTheStart() {
        context.withPropertyValues(SETTING + "=00000000-0000-0000-0000-000000000000").run(started -> {
            assertThat(started).hasFailed();
            assertThat(fullReport(started.getStartupFailure())).contains(SETTING);
        });
    }

    @Test
    void canonicalSetting_starts() {
        context.withPropertyValues(SETTING + "=F3876C53-C7D8-4620-B364-9538164BA5D4")
                .run(started -> assertThat(started).hasNotFailed().hasSingleBean(ContragentMapper.class));
    }

    @Test
    void missingSetting_starts() {
        context.run(started -> assertThat(started).hasNotFailed().hasSingleBean(ContragentMapper.class));
    }

    private static String fullReport(Throwable failure) {
        StringWriter report = new StringWriter();
        failure.printStackTrace(new PrintWriter(report));
        return report.toString();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(OneCContractProperties.class)
    @Import({ContragentMapper.class, ForeignReferences.class})
    static class MapperOnly {

        @Bean
        LpuRepository lpuRepository() {
            return mock(LpuRepository.class);
        }

        @Bean
        InsurerRepository insurerRepository() {
            return mock(InsurerRepository.class);
        }

        @Bean
        ForeignReferenceRepository foreignReferenceRepository() {
            return mock(ForeignReferenceRepository.class);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
