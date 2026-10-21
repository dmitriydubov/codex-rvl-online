package ru.projectx.datareference.controller.openapi;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.web.method.HandlerMethod;

final class EndpointComparison {

    private final OpenApiDescription description;
    private final Mismatches mismatches;

    EndpointComparison(OpenApiDescription description, Mismatches mismatches) {
        this.description = description;
        this.mismatches = mismatches;
    }

    void compare(Map<String, List<HandlerMethod>> endpoints, String basePath) {
        Map<String, YamlNode> operations = describedUnder(basePath);
        mismatches.compareNames("перечень ручек", "ручка", endpoints.keySet(), operations.keySet());
        endpoints.forEach((key, handlers) -> {
            if (operations.containsKey(key)) {
                compareParameters(key, OperationParameters.of(handlers), operations.get(key));
            }
        });
    }

    Map<String, YamlNode> describedUnder(String basePath) {
        Map<String, YamlNode> operations = new TreeMap<>(description.operations());
        operations.keySet().removeIf(key -> !isUnder(key, basePath));
        return operations;
    }

    private static boolean isUnder(String operationKey, String basePath) {
        String path = operationKey.substring(operationKey.indexOf(' ') + 1);
        return path.equals(basePath) || path.startsWith(basePath + "/");
    }

    private void compareParameters(String key, Map<String, ApiParameter> inCode, YamlNode operation) {
        Map<String, ApiParameter> described = OperationParameters.described(description.parameters(operation));
        mismatches.compareNames(key, "параметр", inCode.keySet(), described.keySet());
        inCode.values().stream()
                .filter(parameter -> described.containsKey(parameter.name()))
                .forEach(parameter -> compareParameter(key, parameter, described.get(parameter.name())));
    }

    private void compareParameter(String key, ApiParameter inCode, ApiParameter described) {
        String where = key + ", параметр «" + inCode.name() + "»";
        mismatches.whenDiffer(where, "расположение (in)", inCode.location(), described.location());
        mismatches.whenDiffer(where, "обязательность (required)", inCode.required(), described.required());
        mismatches.whenDiffer(where, "схема (type, format)", inCode.schema(), described.schema());
    }
}
