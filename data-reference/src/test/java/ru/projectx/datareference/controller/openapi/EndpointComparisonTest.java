package ru.projectx.datareference.controller.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;

class EndpointComparisonTest {

    private static final String BASE = "/api";
    private static final String ITEMS = """
            paths:
              /api/items:
                get:
                  parameters:
                    - {name: X-Request-Id, in: header, required: false}
                    - {name: external_id, in: query, required: %s, schema: {type: string, format: uuid}}
                    - {name: number, in: query, required: false, schema: {type: string}}
                    - {name: limit, in: query, required: false, schema: {type: integer}}
              /api/items/{id}:
                get:
                  parameters:
                    - {name: id, in: %s, required: true, schema: {type: string, format: uuid}}
              /livez:
                get: {}
            """;

    @Test
    void handlersOfOnePathAreOneOperationWhoseKeysAreOptional() {
        assertThat(compare(ITEMS.formatted("false", "path"))).isEmpty();
    }

    @Test
    void parameterRequiredOnlyInOneOfTheAnsweringHandlersIsReportedAsRequired() {
        assertThat(compare(ITEMS.formatted("true", "path"))).containsExactly(
                "GET /api/items, параметр «external_id»: обязательность (required) — в коде false, в описании true");
    }

    @Test
    void parameterWithAnotherLocationIsReported() {
        assertThat(compare(ITEMS.formatted("false", "query"))).containsExactly(
                "GET /api/items/{id}, параметр «id»: расположение (in) — в коде path, в описании query");
    }

    @Test
    void undescribedParameterAndOperationAreReported() {
        String withoutNumber = ITEMS.replaceAll("(?m)^.*name: number.*\n", "")
                .replace("  /livez:", "  /api/archive:\n    get: {}\n  /livez:");

        assertThat(compare(withoutNumber.formatted("false", "path"))).containsExactly(
                "перечень ручек: ручка «GET /api/archive» есть в описании, в коде нет",
                "GET /api/items: параметр «number» есть в коде, в описании нет");
    }

    @Test
    void requiredParameterOfTheOnlyAnsweringHandlerStaysRequired() {
        Map<String, ApiParameter> parameters = OperationParameters.of(List.of(handler("byNumber"), handler("withoutKey")));

        assertThat(parameters.values()).containsExactly(
                new ApiParameter("limit", ApiParameter.QUERY, false, "integer, format не задан"),
                new ApiParameter("number", ApiParameter.QUERY, true, "string, format не задан"));
    }

    @Test
    void parameterWithAnotherSchemaIsReported() {
        String limitAsText = ITEMS.replace("name: limit, in: query, required: false, schema: {type: integer}",
                "name: limit, in: query, required: false, schema: {type: string}");

        assertThat(compare(limitAsText.formatted("false", "path"))).containsExactly(
                "GET /api/items, параметр «limit»: схема (type, format) — в коде integer, format не задан, "
                        + "в описании string, format не задан");
    }

    private static List<String> compare(String yaml) {
        Mismatches mismatches = new Mismatches();
        new EndpointComparison(OpenApiDescription.parse(yaml), mismatches).compare(Map.of(
                "GET /api/items", List.of(handler("byExternalId"), handler("byNumber"), handler("withoutKey")),
                "GET /api/items/{id}", List.of(handler("byId"))), BASE);
        return mismatches.found();
    }

    private static HandlerMethod handler(String name) {
        return new HandlerMethod(new Items(), ReflectionUtils.findMethod(Items.class, name, (Class<?>[]) null));
    }

    static class Items {

        public String byExternalId(@RequestParam("external_id") UUID externalId) {
            return externalId.toString();
        }

        public String byNumber(@RequestParam String number, @RequestParam(required = false) @Nullable Integer limit) {
            return number + limit;
        }

        public String withoutKey() {
            return "";
        }

        public String byId(@PathVariable UUID id) {
            return id.toString();
        }
    }
}
