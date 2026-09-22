package com.epam.finaltask.service;

import java.util.List;

import com.epam.finaltask.dto.VoucherDTO;
import com.epam.finaltask.dto.VoucherFilterDTO;
import com.epam.finaltask.model.HotelType;
import com.epam.finaltask.model.TourType;
import org.springframework.data.domain.Page;

public interface VoucherService {
    Page<VoucherDTO> findFilteredVouchers(
            VoucherFilterDTO filters, String searchText, List<String> sortBy, List<String> sortDirection, int page, int size
    );
    VoucherDTO create(VoucherDTO voucherDTO);
    VoucherDTO order(String id, String userId);
    VoucherDTO update(String id, VoucherDTO voucherDTO);
    void delete(String voucherId);
    VoucherDTO changeHotStatus(String id, String status);
    List<VoucherDTO> findAllByUserId(String userId);

    List<VoucherDTO> findAllByTourType(TourType tourType);
    List<VoucherDTO> findAllByTransferType(String transferType);
    List<VoucherDTO> findAllByPrice(Double price);
    List<VoucherDTO> findAllByHotelType(HotelType hotelType);

    List<VoucherDTO> findAll();

    void setHot(String id, boolean hotStatus);

    VoucherDTO getById(String id);
}
