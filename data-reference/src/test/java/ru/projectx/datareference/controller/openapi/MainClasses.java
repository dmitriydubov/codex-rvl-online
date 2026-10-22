package ru.projectx.datareference.controller.openapi;

import java.net.URL;
import java.util.List;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.ClassUtils;
import ru.projectx.datareference.controller.InternalApi;

final class MainClasses {

    static final TypeFilter ANY_CLASS = (reader, factory) -> true;

    private static final URL MAIN_OUTPUT = origin(InternalApi.class);

    private MainClasses() {
    }

    static List<Class<?>> in(String packageName, TypeFilter filter) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(filter);
        return scanner.findCandidateComponents(packageName).stream()
                .map(BeanDefinition::getBeanClassName)
                .<Class<?>>map(name -> ClassUtils.resolveClassName(String.valueOf(name), null))
                .filter(type -> MAIN_OUTPUT.equals(origin(type)))
                .toList();
    }

    private static URL origin(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation();
    }
}
