package ru.projectx.datareference.controller.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiErrorCodesTest {

    @Test
    void errorTableRowsGiveOnePairPerQuotedCode() {
        OpenApiDescription description = OpenApiDescription.parse("""
                info:
                  description: |
                    | HTTP | code | Когда |
                    |---|---|---|
                    | 404 | `NOT_FOUND` | нет такого пути |
                    | 409 | `AMBIGUOUS_POLICY`, `AMBIGUOUS_PRICE` | данные 1С неоднозначны; `details` |
                    Текст после таблицы с `CODE_OUTSIDE`.
                """);

        assertThat(ApiErrorCodes.described(description))
                .containsExactly("404 NOT_FOUND", "409 AMBIGUOUS_POLICY", "409 AMBIGUOUS_PRICE");
    }

    @Test
    void declaredCodesAreTheUpperCaseTextConstantsOfExceptionsAndAdvices() {
        assertThat(ApiErrorCodes.declared())
                .contains("POLICY_NOT_FOUND", "AMBIGUOUS_PRICE", "VALIDATION_ERROR", "SERVICE_UNAVAILABLE")
                .doesNotContain("external_id", "обязательный параметр");
    }
}
