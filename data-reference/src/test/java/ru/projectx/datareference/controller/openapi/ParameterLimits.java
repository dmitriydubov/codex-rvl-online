package ru.projectx.datareference.controller.openapi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import ru.projectx.datareference.config.ReferenceApiProperties;
import ru.projectx.datareference.service.rest.ResultLimits;

final class ParameterLimits {

    static final String MKB10 = "GET /internal/v1/mkb10";
    static final String POLICY_SEARCH = "GET /internal/v1/policies/search";

    private static final List<String> KEYWORDS =
            List.of("minLength", "maxLength", "minimum", "maximum", "maxItems", "default");
    private static final Pattern NIGHTS = Pattern.compile("не больше (\\d+) ноч");

    private final Map<String, Integer> limits = new TreeMap<>();

    private ParameterLimits() {
    }

    static Map<String, Integer> described(OpenApiDescription description) {
        ParameterLimits described = new ParameterLimits();
        description.operations().forEach((operation, node) -> description.parameters(node)
                .forEach(parameter -> described.addSchemaOf(operation, parameter)));
        return described.limits;
    }

    static Map<String, Integer> configured(ReferenceApiProperties api) {
        return new ParameterLimits()
                .add(MKB10, "query", "minLength", api.mkb10().minQueryLength())
                .add(MKB10, "query", "maxLength", api.mkb10().maxQueryLength())
                .add(MKB10, "limit", "minimum", ResultLimits.MIN_LIMIT)
                .add(MKB10, "limit", "maximum", api.mkb10().maxLimit())
                .add(MKB10, "limit", "default", api.mkb10().defaultLimit())
                .add(MKB10, "codes", "maxItems", api.mkb10().maxCodes())
                .add(POLICY_SEARCH, "query", "minLength", api.search().minQueryLength())
                .add(POLICY_SEARCH, "query", "maxLength", api.search().maxQueryLength())
                .add(POLICY_SEARCH, "limit", "minimum", ResultLimits.MIN_LIMIT)
                .add(POLICY_SEARCH, "limit", "maximum", api.search().maxLimit())
                .add(POLICY_SEARCH, "limit", "default", api.search().defaultLimit())
                .limits;
    }

    static Set<Integer> nightLimits(String descriptionText) {
        return NIGHTS.matcher(descriptionText).results()
                .map(nights -> Integer.valueOf(nights.group(1)))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    static void compare(Map<String, Integer> configured, Map<String, Integer> described, Mismatches mismatches) {
        mismatches.compareNames("пределы параметров", "предел", configured.keySet(), described.keySet());
        configured.forEach((limit, value) -> {
            if (described.containsKey(limit)) {
                mismatches.whenDiffer(limit, "значение", value, described.get(limit));
            }
        });
    }

    private void addSchemaOf(String operation, YamlNode parameter) {
        String name = parameter.get("name").text().orElse("");
        YamlNode schema = parameter.get("schema");
        KEYWORDS.forEach(keyword -> schema.get(keyword).integer()
                .ifPresent(value -> add(operation, name, keyword, value)));
    }

    private ParameterLimits add(String operation, String parameter, String keyword, int value) {
        limits.put(operation + ", параметр «" + parameter + "», " + keyword, value);
        return this;
    }
}
