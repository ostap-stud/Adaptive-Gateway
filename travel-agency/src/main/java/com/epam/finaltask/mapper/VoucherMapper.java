package com.epam.finaltask.mapper;

import com.epam.finaltask.dto.VoucherDTO;
import com.epam.finaltask.model.Voucher;
import com.epam.finaltask.repository.UserRepository;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class VoucherMapper {

    @Autowired
    protected UserRepository userRepository;

    @Mapping(target = "user", expression = "java(voucherDTO.getUserId() != null ? userRepository.findById(voucherDTO.getUserId()).orElse(null) : null)")
    public abstract Voucher toVoucher(VoucherDTO voucherDTO);

    @Mapping(target = "userId", expression = "java(voucher.getUser() != null ? voucher.getUser().getId() : null)")
    public abstract VoucherDTO toVoucherDTO(Voucher voucher);

    public abstract List<VoucherDTO> toVoucherDTOList(List<Voucher> vouchers);
}
