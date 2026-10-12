package ru.projectx.datareference.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.web.bind.WebDataBinder;

class UuidParametersTest {

    private final WebDataBinder binder = new WebDataBinder(null);

    @Test
    void nonCanonicalRecord_isNotReadByTheStandardFallback() {
        binder.setConversionService(new DefaultFormattingConversionService());
        new UuidParameters().readUuidStrictly(binder);

        assertThatExceptionOfType(TypeMismatchException.class)
                .isThrownBy(() -> binder.convertIfNecessary("1-2-3-4-5", UUID.class));
    }

    @Test
    void canonicalRecordInUpperCase_isRead() {
        new UuidParameters().readUuidStrictly(binder);

        assertThat(binder.convertIfNecessary("77FF17B5-E659-4720-B57F-E8A19C00FCEA", UUID.class))
                .isEqualTo(UUID.fromString("77ff17b5-e659-4720-b57f-e8a19c00fcea"));
    }
}
