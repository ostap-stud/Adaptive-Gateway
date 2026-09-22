package com.epam.finaltask.service.specification;

import com.epam.finaltask.model.Voucher;
import org.springframework.data.jpa.domain.Specification;

public class VoucherSpecification {

    public static Specification<Voucher> containsTitle(String searchTitle) {
        return (root, query, cb) -> {
            if (searchTitle != null && !searchTitle.isBlank()) {
                return cb.like(cb.lower(root.get("title")), "%" + searchTitle.toLowerCase() + "%");
            }
            return cb.conjunction();
        };
    }

    public static Specification<Voucher> hasEqualTourType(String tourType) {
        return (root, query, cb) -> {
            if (tourType != null && !tourType.isBlank()) {
                return cb.equal(root.get("tourType"), tourType);
            }
            return cb.conjunction();
        };
    }

    public static Specification<Voucher> hasPriceGreaterThanOrEqual(Double minPrice) {
        return (root, query, cb) -> {
            if (minPrice != null) {
                return cb.greaterThanOrEqualTo(root.get("price"), minPrice);
            }
            return cb.conjunction();
        };
    }

    public static Specification<Voucher> hasPriceLessThanOrEqual(Double maxPrice) {
        return (root, query, cb) -> {
            if (maxPrice != null) {
                return cb.lessThanOrEqualTo(root.get("price"), maxPrice);
            }
            return cb.conjunction();
        };
    }

    public static Specification<Voucher> hasEqualTransferType(String transferType) {
        return (root, query, cb) -> {
            if (transferType != null && !transferType.isBlank()) {
                return cb.equal(root.get("transferType"), transferType);
            }
            return cb.conjunction();
        };
    }

    public static Specification<Voucher> hasEqualHotelType(String hotelType) {
        return (root, query, cb) -> {
            if (hotelType != null && !hotelType.isBlank()) {
                return cb.equal(root.get("hotelType"), hotelType);
            }
            return cb.conjunction();
        };
    }

}
