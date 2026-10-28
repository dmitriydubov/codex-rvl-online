package ru.projectx.datareference.controller.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ParameterLimitsTest {

    private static final String SEARCH = """
            paths:
              /internal/v1/policies/search:
                get:
                  parameters:
                    - {$ref: '#/components/parameters/Query'}
                    - name: limit
                      in: query
                      schema: {type: integer, minimum: 1, maximum: 50, default: 10}
                    - name: exact
                      in: query
                      schema: {type: boolean, default: false}
            components:
              parameters:
                Query:
                  name: query
                  in: query
                  schema: {type: string, minLength: 2, maxLength: 100}
            """;
    private static final String QUERY_MAX = ParameterLimits.POLICY_SEARCH + ", параметр «query», maxLength";
    private static final String LIMIT_MAX = ParameterLimits.POLICY_SEARCH + ", параметр «limit», maximum";

    @Test
    void numericLimitsOfParameterSchemasAreRead_includingReferencedParameters_butNotBooleanDefaults() {
        Map<String, Integer> described = ParameterLimits.described(OpenApiDescription.parse(SEARCH));

        assertThat(described).containsOnlyKeys(
                ParameterLimits.POLICY_SEARCH + ", параметр «query», minLength", QUERY_MAX,
                ParameterLimits.POLICY_SEARCH + ", параметр «limit», minimum", LIMIT_MAX,
                ParameterLimits.POLICY_SEARCH + ", параметр «limit», default");
        assertThat(described).containsEntry(QUERY_MAX, 100).containsEntry(LIMIT_MAX, 50);
    }

    @Test
    void differentValueIsReportedWithBothSides() {
        Mismatches mismatches = new Mismatches();

        ParameterLimits.compare(Map.of(LIMIT_MAX, 50), Map.of(LIMIT_MAX, 40), mismatches);

        assertThat(mismatches.found()).containsExactly(LIMIT_MAX + ": значение — в коде 50, в описании 40");
    }

    @Test
    void limitWithoutSourceAndSourceWithoutLimitAreBothReported() {
        Mismatches mismatches = new Mismatches();

        ParameterLimits.compare(Map.of(LIMIT_MAX, 50), Map.of(QUERY_MAX, 100), mismatches);

        assertThat(mismatches.found()).containsExactly(
                "пределы параметров: предел «" + LIMIT_MAX + "» есть в коде, в описании нет",
                "пределы параметров: предел «" + QUERY_MAX + "» есть в описании, в коде нет");
    }

    @Test
    void everyNightLimitInTheTextIsFound() {
        assertThat(ParameterLimits.nightLimits("период не больше 366 ночей; выезд не больше 365 ночей после заезда"))
                .containsExactly(365, 366);
    }
}
