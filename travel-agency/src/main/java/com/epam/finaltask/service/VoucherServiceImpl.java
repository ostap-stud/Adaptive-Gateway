package com.epam.finaltask.service;

import com.epam.finaltask.dto.VoucherDTO;
import com.epam.finaltask.dto.VoucherFilterDTO;
import com.epam.finaltask.mapper.VoucherMapper;
import com.epam.finaltask.model.HotelType;
import com.epam.finaltask.model.TourType;
import com.epam.finaltask.model.TransferType;
import com.epam.finaltask.model.Voucher;
import com.epam.finaltask.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.epam.finaltask.service.specification.VoucherSpecification.*;

@Service
@RequiredArgsConstructor
public class VoucherServiceImpl implements VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherMapper voucherMapper;

    @Override
    public Page<VoucherDTO> findFilteredVouchers(
            VoucherFilterDTO filters, String searchText, List<String> sortBy, List<String> sortDirection,
            int page, int size
    ) {
        Specification<Voucher> spec = containsTitle(searchText)
                .and(hasEqualTourType(filters.getFilterTourType()))
                .and(hasEqualTransferType(filters.getFilterTransferType()))
                .and(hasEqualHotelType(filters.getFilterHotelType()))
                .and(hasPriceGreaterThanOrEqual(filters.getFilterMinPrice()))
                .and(hasPriceLessThanOrEqual(filters.getFilterMaxPrice()));
        List<Sort.Order> orderList = new ArrayList<>();
        if (!sortBy.isEmpty() && !sortDirection.isEmpty()) {
            for (int i = 0; i < sortBy.size(); i++) {
                orderList.add(new Sort.Order(
                                sortDirection.get(i).equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC,
                                sortBy.get(i)
                        )
                );
            }
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(orderList));
        Page<Voucher> voucherPage = voucherRepository.findAll(spec, pageable);
        return voucherPage.map(voucherMapper::toVoucherDTO);
    }

    @Override
    public VoucherDTO create(VoucherDTO voucherDTO) {
        Voucher saved = voucherRepository.save(voucherMapper.toVoucher(voucherDTO));
        return voucherMapper.toVoucherDTO(saved);
    }

    @Override
    public VoucherDTO order(String id, String userId) {
        VoucherDTO orderedVoucher = null;
        Optional<Voucher> voucherToOrder = voucherRepository.findById(UUID.fromString(id));
        if (voucherToOrder.isPresent()) {
            orderedVoucher = voucherMapper.toVoucherDTO(voucherToOrder.get());
            if (orderedVoucher.getUserId() == null){
                orderedVoucher.setUserId(UUID.fromString(userId));
                voucherRepository.save(voucherMapper.toVoucher(orderedVoucher));
            }
        }
        return orderedVoucher;
    }

    @Override
    public VoucherDTO update(String id, VoucherDTO voucherDTO) {
        Voucher updated = voucherRepository.findById(UUID.fromString(id)).isPresent() ?
                voucherRepository.save(voucherMapper.toVoucher(voucherDTO)) : null;
        return voucherMapper.toVoucherDTO(updated);
    }

    @Override
    public void delete(String voucherId) {
        voucherRepository.deleteById(UUID.fromString(voucherId));
    }

    @Override
    public VoucherDTO changeHotStatus(String id, String status) {
        VoucherDTO updatedVoucher = getById(id);
        if (updatedVoucher != null){
            updatedVoucher.setStatus(status);
            updatedVoucher = voucherMapper.toVoucherDTO(
                    voucherRepository.save(voucherMapper.toVoucher(updatedVoucher))
            );
        }
        return updatedVoucher;
    }

    @Override
    public List<VoucherDTO> findAllByUserId(String userId) {
        List<VoucherDTO> voucherDTOList = voucherMapper.toVoucherDTOList(
                voucherRepository.findAllByUserId(UUID.fromString(userId))
        );
        return checkNotNull(voucherDTOList);
    }

    @Override
    public List<VoucherDTO> findAllByTourType(TourType tourType) {
        List<VoucherDTO> voucherDTOList = voucherMapper.toVoucherDTOList(
                voucherRepository.findAllByTourType(tourType)
        );
        return checkNotNull(voucherDTOList);
    }

    @Override
    public List<VoucherDTO> findAllByTransferType(String transferType) {
        List<VoucherDTO> voucherDTOList = voucherMapper.toVoucherDTOList(
                voucherRepository.findAllByTransferType(TransferType.valueOf(transferType))
        );
        return checkNotNull(voucherDTOList);
    }

    @Override
    public List<VoucherDTO> findAllByPrice(Double price) {
        List<VoucherDTO> voucherDTOList = voucherMapper.toVoucherDTOList(
                voucherRepository.findAllByPrice(price)
        );
        return checkNotNull(voucherDTOList);
    }

    @Override
    public List<VoucherDTO> findAllByHotelType(HotelType hotelType) {
        List<VoucherDTO> voucherDTOList = voucherMapper.toVoucherDTOList(
                voucherRepository.findAllByHotelType(hotelType)
        );
        return checkNotNull(voucherDTOList);
    }

    @Override
    public List<VoucherDTO> findAll() {
        List<VoucherDTO> voucherDTOList = voucherMapper.toVoucherDTOList(
                voucherRepository.findAll()
        );
        return checkNotNull(voucherDTOList);
    }

    @Override
    public void setHot(String id, boolean hotStatus) {
        VoucherDTO voucherDTO = getById(id);
        if (voucherDTO != null){
            voucherDTO.setHot(hotStatus);
            create(voucherDTO);
        }
    }

    @Override
    public VoucherDTO getById(String id) {
        return voucherRepository.findById(UUID.fromString(id)).map(voucherMapper::toVoucherDTO).orElse(null);
    }

    private List<VoucherDTO> checkNotNull(List<VoucherDTO> voucherDTOList) {
        return voucherDTOList != null ? voucherDTOList : List.of();
    }
}
