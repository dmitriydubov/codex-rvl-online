package ru.projectx.contracts.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Email
@Pattern(regexp = PayerEmailFormat.ASCII_ADDRESS_WITH_DOTTED_DOMAIN)
@Size(max = PayerEmailFormat.MAX_LENGTH)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PayerEmailFormat {

    int MAX_LENGTH = 254;
    String LOCAL_PART = "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+";
    String DOMAIN_LABEL = "[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?";
    String ASCII_ADDRESS_WITH_DOTTED_DOMAIN = LOCAL_PART + "@" + DOMAIN_LABEL + "(\\." + DOMAIN_LABEL + ")+";

    String message() default "адрес электронной почты плательщика не по формату";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
