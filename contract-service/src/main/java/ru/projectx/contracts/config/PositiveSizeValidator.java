package ru.projectx.contracts.config;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.unit.DataSize;

public class PositiveSizeValidator implements ConstraintValidator<PositiveSize, DataSize> {

    @Override
    public boolean isValid(DataSize size, ConstraintValidatorContext context) {
        return size == null || size.toBytes() > 0;
    }
}
