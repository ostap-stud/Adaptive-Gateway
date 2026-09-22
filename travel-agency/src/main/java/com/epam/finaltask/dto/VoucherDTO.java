package com.epam.finaltask.dto;

import com.epam.finaltask.dto.validation.VoucherDatesValidation;
import com.epam.finaltask.dto.validation.EnumValue;
import com.epam.finaltask.model.HotelType;
import com.epam.finaltask.model.TourType;
import com.epam.finaltask.model.TransferType;
import com.epam.finaltask.model.VoucherStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
@VoucherDatesValidation
public class VoucherDTO {

    private String id;

    @NotBlank(message = "Has to be not blank")
    private String title;

    @NotBlank(message = "Has to be not blank")
    private String description;

    @Min(0)
    @NotNull(message = "Required field")
    private Double price;

    @NotEmpty(message = "Required field")
    @EnumValue(enumClass = TourType.class)
    private String tourType;

    @NotEmpty(message = "Required field")
    @EnumValue(enumClass = TransferType.class)
    private String transferType;

    @NotEmpty(message = "Required field")
    @EnumValue(enumClass = HotelType.class)
    private String hotelType;

    @NotEmpty(message = "Required field")
    @EnumValue(enumClass = VoucherStatus.class)
    private String status;

    @NotNull(message = "Required field")
    private LocalDate arrivalDate;

    @NotNull(message = "Required field")
    private LocalDate evictionDate;

	private Boolean hot;

	private UUID userId;

}
