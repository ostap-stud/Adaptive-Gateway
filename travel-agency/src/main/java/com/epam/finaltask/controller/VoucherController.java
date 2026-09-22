package com.epam.finaltask.controller;

import com.epam.finaltask.dto.VoucherDTO;
import com.epam.finaltask.dto.VoucherFilterDTO;
import com.epam.finaltask.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @GetMapping
    public String listVouchers(
            VoucherFilterDTO filter,
            @RequestParam(defaultValue = "") String searchText,
            @RequestParam(defaultValue = "") List<String> sortBy,
            @RequestParam(defaultValue = "") List<String> sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size,
            @RequestParam(required = false) String errorAlert,
            Model model
    ) {
        validateFilter(filter);
        validateSorting(sortBy, sortDirection);
        Page<VoucherDTO> vouchers = voucherService.findFilteredVouchers(
                filter, searchText, sortBy, sortDirection, page, size
        );
        model.addAttribute("vouchers", vouchers.getContent());
        model.addAttribute("filter", filter);
        model.addAttribute("searchText", searchText);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDirection", sortDirection);
        model.addAttribute("currentPage", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", vouchers.getTotalPages());
        model.addAttribute("errorAlert", errorAlert);
        return "voucher/list";
    }

    @PreAuthorize("hasAuthority('ADMIN_CREATE')")
    @GetMapping("voucher/")
    public String createVoucher(@RequestParam(required = false) String errorAlert, Model model) {
        model.addAttribute("voucherDTO", new VoucherDTO());
        model.addAttribute("errorAlert", errorAlert);
        return "voucher/create";
    }

    @PreAuthorize("hasAuthority('ADMIN_CREATE')")
    @PostMapping("voucher/")
    public String createVoucher(@Valid @ModelAttribute VoucherDTO voucherDTO, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "voucher/create";
        }
        voucherService.create(voucherDTO);
        return "redirect:/";
    }

    @PreAuthorize("hasAuthority('ADMIN_UPDATE') or hasAuthority('MANAGER_UPDATE')")
    @GetMapping("voucher/{id}")
    public String editVoucher(@PathVariable String id, @RequestParam(required = false) String errorAlert, Model model) {
        VoucherDTO voucherDTO = voucherService.getById(id);
        if (voucherDTO == null) {
            return "redirect:/";
        }
        model.addAttribute("voucherDTO", voucherDTO);
        model.addAttribute("errorAlert", errorAlert);
        return "voucher/edit";
    }

    @PreAuthorize("hasAuthority('ADMIN_UPDATE') or hasAuthority('MANAGER_UPDATE')")
    @PostMapping("voucher/{id}")
    public String editVoucher(
            @PathVariable String id,
            @Valid @ModelAttribute VoucherDTO voucherDTO,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            return "voucher/edit";
        }
        voucherService.update(id, voucherDTO);
        return "redirect:/";
    }

    @PreAuthorize("hasAuthority('ADMIN_DELETE')")
    @PostMapping("/{id}/delete")
    public String deleteVoucher(@PathVariable String id) {
        voucherService.delete(id);
        return "redirect:/";
    }

    private void validateFilter(VoucherFilterDTO filter) {
        if (filter.getFilterMinPrice() != null && filter.getFilterMinPrice() < 0){
            filter.setFilterMinPrice(0.0);
        }
        if (filter.getFilterMaxPrice() != null && filter.getFilterMaxPrice() < 0){
            filter.setFilterMaxPrice(0.0);
        }
    }

    private void validateSorting(List<String> sortBy, List<String> sortDirection) {
        if (sortBy.size() != sortDirection.size()){
            sortBy.clear();
            sortDirection.clear();
        }
    }

}

