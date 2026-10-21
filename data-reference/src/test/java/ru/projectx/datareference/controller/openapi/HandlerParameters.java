package ru.projectx.datareference.controller.openapi;

import java.util.Arrays;
import java.util.List;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ValueConstants;
import org.springframework.web.method.HandlerMethod;

final class HandlerParameters {

    private static final ParameterNameDiscoverer NAMES = new DefaultParameterNameDiscoverer();

    private HandlerParameters() {
    }

    static List<ApiParameter> of(HandlerMethod handler) {
        return Arrays.stream(handler.getMethodParameters()).map(HandlerParameters::describe).toList();
    }

    private static ApiParameter describe(MethodParameter parameter) {
        parameter.initParameterNameDiscovery(NAMES);
        RequestParam query = parameter.getParameterAnnotation(RequestParam.class);
        if (query != null) {
            return new ApiParameter(name(query.name(), parameter), ApiParameter.QUERY, required(query, parameter));
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null) {
            return new ApiParameter(name(path.name(), parameter), ApiParameter.PATH, true);
        }
        return new ApiParameter(name("", parameter), ApiParameter.UNMAPPED, false);
    }

    private static boolean required(RequestParam query, MethodParameter parameter) {
        return query.required() && ValueConstants.DEFAULT_NONE.equals(query.defaultValue()) && !parameter.isOptional();
    }

    private static String name(String declared, MethodParameter parameter) {
        return declared.isEmpty() ? String.valueOf(parameter.getParameterName()) : declared;
    }
}
