package ru.projectx.datareference.controller.openapi;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.ResolvableType;
import ru.projectx.datareference.controller.openapi.JsonProperties.JsonProperty;

final class SchemaComparison {

    private final OpenApiDescription description;
    private final JsonProperties properties;
    private final Mismatches mismatches;

    SchemaComparison(OpenApiDescription description, JsonProperties properties, Mismatches mismatches) {
        this.description = description;
        this.properties = properties;
        this.mismatches = mismatches;
    }

    void compare(ResolvableType type, YamlNode schema, String where) {
        SchemaView view = SchemaView.of(description, schema);
        Class<?> javaType = type.toClass();
        if (javaType.isRecord()) {
            compareObject(javaType, view, where);
        } else if (javaType.isSealed()) {
            compareVariants(javaType, view, where);
        } else {
            compareValue(type, view, where);
        }
    }

    private void compareValue(ResolvableType type, SchemaView view, String where) {
        mismatches.whenDiffer(where, "тип", JsonTypes.of(type.toClass()), view.type());
        ResolvableType collection = type.asCollection();
        if (collection != ResolvableType.NONE) {
            compare(collection.getGeneric(0), view.items(), where + "[]");
        }
    }

    private void compareObject(Class<?> type, SchemaView view, String where) {
        mismatches.whenDiffer(where, "тип", JsonTypes.OBJECT, view.type());
        List<JsonProperty> written = properties.of(type);
        mismatches.compareNames(where, "поле", JsonProperties.names(written), view.properties().keySet());
        written.stream()
                .filter(property -> view.properties().containsKey(property.name()))
                .forEach(property -> compareProperty(property, view, where + "." + property.name()));
    }

    private void compareProperty(JsonProperty property, SchemaView owner, String where) {
        YamlNode schema = owner.properties().get(property.name());
        mismatches.whenDiffer(where, "всегда в ответе (required)", property.alwaysWritten(),
                owner.required().contains(property.name()));
        mismatches.whenDiffer(where, "допустимость null (nullable)", property.writtenAsNull(),
                SchemaView.of(description, schema).nullable());
        compare(property.type(), schema, where);
    }

    private void compareVariants(Class<?> type, SchemaView view, String where) {
        List<Class<?>> variants = List.of(type.getPermittedSubclasses());
        mismatches.whenDiffer(where, "число вариантов (oneOf)", variants.size(), view.oneOf().size());
        variants.forEach(variant -> variantOf(variant, view).ifPresentOrElse(
                schema -> compare(ResolvableType.forClass(variant), schema, where + "<" + variant.getSimpleName() + ">"),
                () -> mismatches.add(where, "нет варианта oneOf с полями " + fieldsOf(variant))));
    }

    private Optional<YamlNode> variantOf(Class<?> variant, SchemaView view) {
        return view.oneOf().stream()
                .filter(schema -> SchemaView.of(description, schema).properties().keySet().equals(fieldsOf(variant)))
                .findFirst();
    }

    private Set<String> fieldsOf(Class<?> variant) {
        return JsonProperties.names(properties.of(variant));
    }
}
