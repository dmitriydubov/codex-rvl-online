package ru.projectx.datareference.controller.openapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.core.Nullness;
import org.springframework.core.ResolvableType;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.introspect.ClassIntrospector;

final class JsonProperties {

    private static final Set<JsonInclude.Include> ALWAYS_WRITTEN =
            Set.of(JsonInclude.Include.ALWAYS, JsonInclude.Include.USE_DEFAULTS);

    private final ObjectMapper mapper;

    JsonProperties(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    List<JsonProperty> of(Class<?> type) {
        SerializationConfig config = mapper.serializationConfig();
        ClassIntrospector introspector = config.classIntrospectorInstance();
        JavaType javaType = mapper.constructType(type);
        BeanDescription bean = introspector.introspectForSerialization(javaType,
                introspector.introspectClassAnnotations(javaType));
        JsonInclude.Value beanInclusion = beanInclusion(config, bean);
        return bean.findProperties().stream()
                .map(property -> describe(type, property, config, beanInclusion))
                .toList();
    }

    Set<String> enumValues(Class<?> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .map(constant -> mapper.valueToTree(constant).asString())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    static Set<String> names(List<JsonProperty> properties) {
        return properties.stream().map(JsonProperty::name).collect(Collectors.toCollection(TreeSet::new));
    }

    private static JsonProperty describe(
            Class<?> type, BeanPropertyDefinition property, SerializationConfig config, JsonInclude.Value beanInclusion) {

        Method accessor = property.getGetter().getAnnotated();
        JsonInclude.Value inclusion = config.getDefaultInclusion(accessor.getReturnType(), accessor.getReturnType(), beanInclusion)
                .withOverrides(property.findInclusion());
        boolean alwaysWritten = ALWAYS_WRITTEN.contains(inclusion.getValueInclusion());
        boolean writtenAsNull = alwaysWritten && Nullness.forMethodReturnType(accessor) == Nullness.NULLABLE;
        return new JsonProperty(property.getName(), alwaysWritten, writtenAsNull,
                ResolvableType.forMethodReturnType(accessor, type));
    }

    private static JsonInclude.Value beanInclusion(SerializationConfig config, BeanDescription bean) {
        JsonInclude.Value perType = JsonInclude.Value.merge(bean.findPropertyInclusion(JsonInclude.Value.empty()),
                config.getDefaultPropertyInclusion(bean.getBeanClass(), JsonInclude.Value.empty()));
        return JsonInclude.Value.merge(config.getDefaultPropertyInclusion(), perType);
    }

    record JsonProperty(String name, boolean alwaysWritten, boolean writtenAsNull, ResolvableType type) {
    }
}
