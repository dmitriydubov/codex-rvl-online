package ru.projectx.datareference.controller.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import tools.jackson.databind.json.JsonMapper;

class SchemaValueComparisonTest {

    private static final String RESULT = """
            components:
              schemas:
                Result:
                  type: object
                  required: [outcome, detail, day]
                  properties:
                    outcome: {type: string, enum: [DONE, FAILED]}
                    detail:
                      oneOf:
                        - {type: object, required: [field], properties: {field: {type: string}}}
                        - {type: object, required: [day], properties: {day: {type: string, format: date}}}
                    day: {type: string, format: date}
            """;
    private static final String DAY_VARIANT =
            "            - {type: object, required: [day], properties: {day: {type: string, format: date}}}\n";

    @Test
    void matchingEnumVariantsAndFormatsHaveNoMismatches() {
        assertThat(compare(RESULT)).isEmpty();
    }

    @Test
    void enumConstantMissingFromTheDescriptionIsReported() {
        assertThat(compare(RESULT.replace("[DONE, FAILED]", "[DONE]"))).containsExactly(
                "Result.outcome: значение enum «FAILED» есть в коде, в описании нет");
    }

    @Test
    void enumValueMissingFromTheCodeIsReported() {
        assertThat(compare(RESULT.replace("[DONE, FAILED]", "[DONE, FAILED, SKIPPED]"))).containsExactly(
                "Result.outcome: значение enum «SKIPPED» есть в описании, в коде нет");
    }

    @Test
    void sealedVariantWithoutItsOneOfSchemaIsReported() {
        assertThat(compare(RESULT.replace(DAY_VARIANT, ""))).containsExactlyInAnyOrder(
                "Result.detail: число вариантов (oneOf) — в коде 2, в описании 1",
                "Result.detail: нет варианта oneOf с полями [day]");
    }

    @Test
    void variantSchemaIsComparedWithItsRecord() {
        String uuidDay = RESULT.replace(DAY_VARIANT, DAY_VARIANT.replace("format: date", "format: uuid"));

        assertThat(compare(uuidDay)).containsExactly(
                "Result.detail<DayDetail>.day: формат (format) — в коде date, в описании uuid");
    }

    @Test
    void formatOfTheJavaTypeIsCompared() {
        String withoutFormat = RESULT.replace("    day: {type: string, format: date}", "    day: {type: string}");

        assertThat(compare(withoutFormat)).containsExactly(
                "Result.day: формат (format) — в коде date, в описании не задан");
    }

    @Test
    void requiredNameWithoutAPropertyIsReported() {
        assertThat(compare(RESULT.replace("[outcome, detail, day]", "[outcome, detail, day, stale]")))
                .containsExactly("Result: «stale» есть в required, в properties нет");
    }

    private static List<String> compare(String yaml) {
        OpenApiDescription description = OpenApiDescription.parse(yaml);
        Mismatches mismatches = new Mismatches();
        new SchemaComparison(description, new JsonProperties(JsonMapper.builder().build()), mismatches)
                .compare(ResolvableType.forClass(Result.class), description.component("schemas", "Result"), "Result");
        return mismatches.found();
    }

    record Result(Outcome outcome, Detail detail, LocalDate day) {
    }

    enum Outcome {
        DONE,
        FAILED
    }

    sealed interface Detail permits FieldDetail, DayDetail {
    }

    record FieldDetail(String field) implements Detail {
    }

    record DayDetail(LocalDate day) implements Detail {
    }
}
