package ru.projectx.datareference.controller.openapi;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import ru.projectx.datareference.controller.error.RequestErrorAdvice;

final class AdviceHandlers {

    private AdviceHandlers() {
    }

    static Set<String> unreachedBy(List<Exception> failures) {
        List<ExceptionHandlerMethodResolver> advicesInOrder = adviceTypes().stream()
                .sorted(Comparator.comparingInt(AdviceHandlers::order))
                .map(ExceptionHandlerMethodResolver::new)
                .toList();
        Set<Method> reached = failures.stream()
                .flatMap(failure -> handlerOf(failure, advicesInOrder).stream())
                .collect(Collectors.toSet());
        return adviceTypes().stream()
                .flatMap(advice -> Arrays.stream(advice.getDeclaredMethods()))
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, ExceptionHandler.class))
                .filter(method -> !reached.contains(method))
                .map(method -> method.getDeclaringClass().getSimpleName() + "." + method.getName())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Optional<Method> handlerOf(Exception failure, List<ExceptionHandlerMethodResolver> advicesInOrder) {
        return advicesInOrder.stream()
                .map(advice -> advice.resolveMethodByThrowable(failure))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private static int order(Class<?> advice) {
        return OrderUtils.getOrder(advice, Ordered.LOWEST_PRECEDENCE);
    }

    private static List<Class<?>> adviceTypes() {
        return MainClasses.in(RequestErrorAdvice.class.getPackageName(), new AnnotationTypeFilter(ControllerAdvice.class));
    }
}
