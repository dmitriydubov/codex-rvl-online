package ru.projectx.datareference.controller.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class SchemaComparisonTest {

    private static final String PAGE = """
            components:
              schemas:
                Page:
                  type: object
                  required: [items]
                  properties:
                    items: {type: array, items: {$ref: '#/components/schemas/Item'}}
                Item:
                  type: object
                  required: %s
                  properties:
                    item_name: {type: string}
                    note: {type: string, nullable: %s}
                    tags: {type: array, items: {type: string}}
                    owner: {type: object, nullable: true, allOf: [{$ref: '#/components/schemas/Owner'}]}
                Owner:
                  type: object
                  required: [id]
                  properties:
                    id: {type: %s}
            """;
    private static final String ALL_WRITTEN = "[item_name, note, owner]";

    @Test
    void matchingSchemaHasNoMismatches() {
        assertThat(mismatches(ALL_WRITTEN, "true", "integer")).isEmpty();
    }

    @Test
    void propertyNamesComeFromTheConfiguredNamingStrategy() {
        String camelCase = PAGE.replace("item_name:", "itemName:");

        assertThat(compare(camelCase.formatted("[itemName, note, owner]", "true", "integer"))).containsExactly(
                "Page.items[]: поле «item_name» есть в коде, в описании нет",
                "Page.items[]: поле «itemName» есть в описании, в коде нет");
    }

    @Test
    void nullableComponentWithoutNullableInTheSchemaIsReported() {
        assertThat(mismatches(ALL_WRITTEN, "false", "integer")).containsExactly(
                "Page.items[].note: допустимость null (nullable) — в коде true, в описании false");
    }

    @Test
    void alwaysWrittenPropertyMissingFromRequiredIsReported() {
        assertThat(mismatches("[item_name, owner]", "true", "integer")).containsExactly(
                "Page.items[].note: всегда в ответе (required) — в коде true, в описании false");
    }

    @Test
    void propertyOmittedWhenNullIsNeitherRequiredNorNullable() {
        assertThat(mismatches("[item_name, note, owner, tags]", "true", "integer")).containsExactly(
                "Page.items[].tags: всегда в ответе (required) — в коде false, в описании true");
    }

    @Test
    void typeOfANestedRecordBehindAllOfIsCompared() {
        assertThat(mismatches(ALL_WRITTEN, "true", "string")).containsExactly(
                "Page.items[].owner.id: тип — в коде integer, в описании string");
    }

    private static List<String> mismatches(String required, String nullable, String ownerIdType) {
        return compare(PAGE.formatted(required, nullable, ownerIdType));
    }

    private static List<String> compare(String yaml) {
        OpenApiDescription description = OpenApiDescription.parse(yaml);
        JsonMapper mapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        Mismatches mismatches = new Mismatches();
        new SchemaComparison(description, new JsonProperties(mapper), mismatches)
                .compare(ResolvableType.forClass(Page.class), description.component("schemas", "Page"), "Page");
        return mismatches.found();
    }

    record Page(List<Item> items) {
    }

    record Item(
            String itemName,
            @Nullable String note,
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable List<String> tags,
            @Nullable Owner owner) {
    }

    record Owner(long id) {
    }
}
