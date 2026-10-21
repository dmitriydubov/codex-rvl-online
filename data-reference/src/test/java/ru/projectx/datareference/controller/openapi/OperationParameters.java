package ru.projectx.datareference.controller.openapi;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.web.method.HandlerMethod;

final class OperationParameters {

    private OperationParameters() {
    }

    static Map<String, ApiParameter> of(List<HandlerMethod> handlers) {
        List<List<ApiParameter>> bound = handlers.stream().map(HandlerParameters::of).toList();
        List<List<ApiParameter>> answering = bound.stream().filter(OperationParameters::bindsQuery).toList();
        return bound.stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(ApiParameter::name, parameter -> merged(parameter, answering),
                        ApiParameter::orConflictWith, TreeMap::new));
    }

    static Map<String, ApiParameter> described(List<YamlNode> parameters) {
        return parameters.stream()
                .map(ApiParameter::described)
                .filter(parameter -> !ApiParameter.HEADER.equals(parameter.location()))
                .collect(Collectors.toMap(ApiParameter::name, parameter -> parameter,
                        (first, second) -> first, TreeMap::new));
    }

    private static ApiParameter merged(ApiParameter parameter, List<List<ApiParameter>> answering) {
        if (!parameter.inQuery()) {
            return parameter;
        }
        boolean required = answering.stream().allMatch(handler -> requiredIn(handler, parameter.name()));
        return parameter.withRequired(required);
    }

    private static boolean requiredIn(List<ApiParameter> handler, String name) {
        return handler.stream().anyMatch(parameter -> parameter.name().equals(name) && parameter.required());
    }

    private static boolean bindsQuery(List<ApiParameter> handler) {
        return handler.stream().anyMatch(ApiParameter::inQuery);
    }
}
