package ru.projectx.contracts.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.projectx.contracts.entity.contract.AcceptanceChannel;

class PayerEmailFormatTest {

    private static final String LONGEST_LOCAL_PART = "payer-" + "a".repeat(58);
    private static final String LONGEST_ADDRESS = LONGEST_LOCAL_PART + "@stand-" + "b".repeat(55) + ".stand-"
            + "c".repeat(55) + ".stand-" + "d".repeat(54) + ".test";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"insured@example.test", "second.payer@example.test", "payer+lk_2026@mail.example.test",
        "p@e.t", "o'neil-1@stand-1.example.test", "insured@mail.example.test", "insured@example.c0m",
        "insured@1example.test", "insured@192.0.2.test", "insured@example.t-1"})
    void ordinaryAsciiAddressWithDottedDomain_isAccepted(String address) {
        assertThat(violationsOf(address)).isEmpty();
    }

    @Test
    void absentAddress_doesNotBreakTheFormat() {
        assertThat(violationsOf(null)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "insured.example.test", "insured@", "@example.test", "insured @example.test",
        "insured@exam ple.test", "страхователь@example.test", "insured@пример.тест", "insured@localhost",
        "\"insured\"@example.test", "insured@[192.0.2.1]", "insured@example..test", "insured@-example.test",
        "insured@example-.test", "insured@example.test.", "insured@@example.test", "42", "insured@192.0.2.1",
        "insured@example.123", "insured@example.1com", "insured@example.-com", "insured@example.com-"})
    void addressOutOfFormat_isOneViolationOfTheEmailFieldWithoutTheAddressInItsText(String address) {
        Set<ConstraintViolation<AcceptanceRequest>> violations = violationsOf(address);

        assertThat(violations).singleElement().satisfies(violation -> {
            assertThat(violation.getPropertyPath()).hasToString("email");
            assertThat(violation.getMessage()).isEqualTo("адрес электронной почты плательщика не по формату");
        });
    }

    @Test
    void addressOf254Characters_isAccepted() {
        assertThat(LONGEST_ADDRESS).hasSize(254);
        assertThat(violationsOf(LONGEST_ADDRESS)).isEmpty();
    }

    @Test
    void addressOf255Characters_isRefusedByTheLengthLimitAlone() {
        String tooLong = LONGEST_ADDRESS.replace("d.test", "dd.test");

        assertThat(tooLong).hasSize(255);
        assertThat(violationsOf(tooLong)).singleElement()
                .satisfies(violation -> assertThat(violation.getPropertyPath()).hasToString("email"));
    }

    private static Set<ConstraintViolation<AcceptanceRequest>> violationsOf(String address) {
        return validator.validate(new AcceptanceRequest(1, UUID.fromString("b0a1c7e4-52d9-4f31-8c06-7e3a9b1d2f45"),
                OffsetDateTime.parse("2026-02-10T10:04:58+03:00"), AcceptanceChannel.WEB, null, null, address));
    }
}
