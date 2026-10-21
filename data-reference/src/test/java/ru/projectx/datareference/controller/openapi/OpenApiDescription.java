package ru.projectx.datareference.controller.openapi;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

final class OpenApiDescription {

    static final Path FILE = Path.of("docs", "openapi.yaml");

    private static final String REF = "$ref";
    private static final String LOCAL_REF = "#/";
    private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options");

    private final YamlNode root;

    private OpenApiDescription(YamlNode root) {
        this.root = root;
    }

    static OpenApiDescription read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return new OpenApiDescription(new YamlNode(yaml().load(reader)));
        }
    }

    static OpenApiDescription parse(String text) {
        return new OpenApiDescription(new YamlNode(yaml().load(text)));
    }

    Set<String> paths() {
        return root.get("paths").entries().keySet();
    }

    Map<String, YamlNode> operations() {
        Map<String, YamlNode> operations = new TreeMap<>();
        root.get("paths").entries().forEach((path, item) -> item.entries().entrySet().stream()
                .filter(method -> HTTP_METHODS.contains(method.getKey()))
                .forEach(method -> operations.put(operationKey(method.getKey(), path), method.getValue())));
        return operations;
    }

    List<YamlNode> parameters(YamlNode operation) {
        return operation.get("parameters").items().stream().map(this::resolve).toList();
    }

    YamlNode okSchema(YamlNode operation) {
        return resolve(operation.get("responses").get("200")).get("content").get("application/json").get("schema");
    }

    YamlNode component(String section, String name) {
        return root.get("components").get(section).get(name);
    }

    String summary() {
        return root.get("info").get("description").text().orElse("");
    }

    YamlNode resolve(YamlNode node) {
        return node.get(REF).text()
                .filter(ref -> ref.startsWith(LOCAL_REF))
                .map(this::at)
                .map(this::resolve)
                .orElse(node);
    }

    static String operationKey(String method, String path) {
        return method.toUpperCase(Locale.ROOT) + " " + path;
    }

    private YamlNode at(String ref) {
        return Stream.of(ref.substring(LOCAL_REF.length()).split("/"))
                .reduce(root, YamlNode::get, (first, second) -> second);
    }

    private static Yaml yaml() {
        return new Yaml(new SafeConstructor(new LoaderOptions()));
    }
}
