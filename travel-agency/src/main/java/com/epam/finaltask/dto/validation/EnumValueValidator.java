package com.epam.finaltask.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Arrays;
import java.util.List;

public class EnumValueValidator implements ConstraintValidator<EnumValue, CharSequence> {

    private List<String> validValues;

    @Override
    public void initialize(EnumValue constraintAnnotation) {
        validValues = Arrays.stream(constraintAnnotation.enumClass().getEnumConstants()).map(Enum::name).toList();
    }

    @Override
    public boolean isValid(CharSequence charSequence, ConstraintValidatorContext constraintValidatorContext) {
        if (charSequence == null || charSequence.isEmpty()) {
            return true;
        }
        return validValues.contains(charSequence.toString());
    }
}
