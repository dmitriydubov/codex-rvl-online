package ru.projectx.datareference.controller.openapi;

import static org.mockito.Mockito.mock;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import ru.projectx.datareference.config.JacksonConfig;
import ru.projectx.datareference.controller.InternalApi;

final class ApiContext {

    private ApiContext() {
    }

    static WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, WebMvcAutoConfiguration.class))
                .withUserConfiguration(JacksonConfig.class)
                .withInitializer(ApiContext::registerControllers);
    }

    static <T> T withMockedReads(Class<T> controller) {
        Constructor<?> constructor = controller.getConstructors()[0];
        Object[] reads = Arrays.stream(constructor.getParameterTypes()).map(type -> mock(type)).toArray();
        return controller.cast(BeanUtils.instantiateClass(constructor, reads));
    }

    private static void registerControllers(ConfigurableApplicationContext context) {
        GenericApplicationContext registry = (GenericApplicationContext) context;
        MainClasses.in(InternalApi.class.getPackageName(), new AnnotationTypeFilter(InternalApi.class))
                .forEach(controller -> register(registry, controller));
    }

    private static <T> void register(GenericApplicationContext registry, Class<T> controller) {
        registry.registerBean(controller, () -> withMockedReads(controller));
    }
}
