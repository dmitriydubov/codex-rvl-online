package ru.projectx.datareference.controller.openapi;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import ru.projectx.datareference.controller.InternalApi;

final class CodeEndpoints {

    private static final String ANY_METHOD = "ANY";

    private CodeEndpoints() {
    }

    static Map<String, List<HandlerMethod>> of(RequestMappingHandlerMapping mapping) {
        return mapping.getHandlerMethods().entrySet().stream()
                .filter(handler -> isInternalApi(handler.getValue()))
                .flatMap(handler -> keys(handler.getKey()).map(key -> Map.entry(key, handler.getValue())))
                .collect(Collectors.groupingBy(Map.Entry::getKey, TreeMap::new,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private static Stream<String> keys(RequestMappingInfo info) {
        return info.getPatternValues().stream()
                .flatMap(path -> methods(info).map(method -> OpenApiDescription.operationKey(method, path)));
    }

    private static Stream<String> methods(RequestMappingInfo info) {
        List<String> methods = info.getMethodsCondition().getMethods().stream().map(Enum::name).toList();
        return methods.isEmpty() ? Stream.of(ANY_METHOD) : methods.stream();
    }

    private static boolean isInternalApi(HandlerMethod handler) {
        return AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), InternalApi.class);
    }
}
