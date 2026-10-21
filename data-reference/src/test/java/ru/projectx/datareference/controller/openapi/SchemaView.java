package ru.projectx.datareference.controller.openapi;

import java.util.List;
import java.util.Map;
import java.util.Set;

record SchemaView(YamlNode node, boolean nullable) {

    static SchemaView of(OpenApiDescription description, YamlNode schema) {
        YamlNode resolved = description.resolve(schema);
        List<YamlNode> allOf = resolved.get("allOf").items();
        boolean nullable = resolved.get("nullable").isTrue();
        YamlNode body = allOf.size() == 1 ? description.resolve(allOf.getFirst()) : resolved;
        return new SchemaView(body, nullable);
    }

    String type() {
        return node.get("type").text().orElse("не задан");
    }

    Map<String, YamlNode> properties() {
        return node.get("properties").entries();
    }

    Set<String> required() {
        return node.get("required").texts();
    }

    YamlNode items() {
        return node.get("items");
    }

    List<YamlNode> oneOf() {
        return node.get("oneOf").items();
    }
}
