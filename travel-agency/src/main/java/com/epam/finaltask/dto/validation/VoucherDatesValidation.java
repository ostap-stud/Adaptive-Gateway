package com.epam.finaltask.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = VoucherDatesValidator.class)
public @interface VoucherDatesValidation {
    String message() default "Arrival has to be before eviction!";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
