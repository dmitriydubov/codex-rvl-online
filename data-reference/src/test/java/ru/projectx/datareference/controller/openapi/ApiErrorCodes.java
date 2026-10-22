package ru.projectx.datareference.controller.openapi;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import ru.projectx.datareference.controller.error.RequestErrorAdvice;
import ru.projectx.datareference.exception.MissingReference;

final class ApiErrorCodes {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]+");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\|\\s*(\\d{3})\\s*\\|([^|]*)\\|", Pattern.MULTILINE);
    private static final Pattern QUOTED_CODE = Pattern.compile("`(" + CODE.pattern() + ")`");
    private static final List<String> CODE_PACKAGES =
            List.of(MissingReference.class.getPackageName(), RequestErrorAdvice.class.getPackageName());

    private ApiErrorCodes() {
    }

    static Set<String> described(OpenApiDescription description) {
        Set<String> pairs = new TreeSet<>();
        Matcher row = TABLE_ROW.matcher(description.summary());
        while (row.find()) {
            String status = row.group(1);
            QUOTED_CODE.matcher(row.group(2)).results().forEach(code -> pairs.add(pair(status, code.group(1))));
        }
        return pairs;
    }

    static String pair(Object status, String code) {
        return status + " " + code;
    }

    static Set<String> declared() {
        return CODE_PACKAGES.stream()
                .flatMap(packageName -> MainClasses.in(packageName, MainClasses.ANY_CLASS).stream())
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .filter(ApiErrorCodes::isTextConstant)
                .map(ApiErrorCodes::constantValue)
                .filter(CODE.asMatchPredicate())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    static List<Class<?>> handledFailures() {
        return MainClasses.in(RequestErrorAdvice.class.getPackageName(), new AnnotationTypeFilter(ControllerAdvice.class))
                .stream()
                .flatMap(advice -> Arrays.stream(advice.getDeclaredMethods()))
                .flatMap(ApiErrorCodes::handledBy)
                .toList();
    }

    private static Stream<Class<?>> handledBy(Method method) {
        ExceptionHandler handler = AnnotatedElementUtils.findMergedAnnotation(method, ExceptionHandler.class);
        return handler == null ? Stream.empty() : Arrays.stream(handler.value());
    }

    private static boolean isTextConstant(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) && field.getType() == String.class;
    }

    private static String constantValue(Field field) {
        ReflectionUtils.makeAccessible(field);
        return String.valueOf(ReflectionUtils.getField(field, null));
    }
}
