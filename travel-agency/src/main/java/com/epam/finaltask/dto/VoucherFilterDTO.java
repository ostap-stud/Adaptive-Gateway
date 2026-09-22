package com.epam.finaltask.dto;

import lombok.Data;

@Data
public class VoucherFilterDTO {
    private String filterTourType;
    private Double filterMinPrice;
    private Double filterMaxPrice;
    private String filterTransferType;
    private String filterHotelType;
}
