package com.epam.finaltask.dto.validation;

import com.epam.finaltask.dto.VoucherDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class VoucherDatesValidator implements ConstraintValidator<VoucherDatesValidation, VoucherDTO> {
    @Override
    public boolean isValid(VoucherDTO voucherDTO, ConstraintValidatorContext constraintValidatorContext) {
        if (voucherDTO.getArrivalDate() == null || voucherDTO.getEvictionDate() == null) {
            return true;
        }
        boolean isValid = voucherDTO.getArrivalDate().isBefore(voucherDTO.getEvictionDate());
        if (!isValid){
            constraintValidatorContext.disableDefaultConstraintViolation();
            constraintValidatorContext.buildConstraintViolationWithTemplate(
                    constraintValidatorContext.getDefaultConstraintMessageTemplate()
            ).addPropertyNode("arrivalDate").addConstraintViolation();
        }
        return isValid;
    }
}
