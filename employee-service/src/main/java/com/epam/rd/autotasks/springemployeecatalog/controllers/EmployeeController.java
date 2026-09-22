package com.epam.rd.autotasks.springemployeecatalog.controllers;

import com.epam.rd.autotasks.springemployeecatalog.domain.Employee;
import com.epam.rd.autotasks.springemployeecatalog.services.EmployeeService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping(value = "/employee")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final Set<String> sortParams = Set.of("lastName", "hired", "position", "salary");

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping(value = "/{employeeId}")
    public Employee getEmployee(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Optional<Boolean> full_chain
    ) {
        return employeeService.getEmployeeById(employeeId, full_chain.orElse(false));
    }

    @PostMapping
    public Employee createEmployee(@RequestBody Employee employee) {
        return employeeService.save(employee, employee.getManager());
    }

    @GetMapping
    public List<Employee> getAllEmployees(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort
    ) {
        Sort sorting = Sort.unsorted();
        if (sort != null && !sort.isEmpty() && sortParams.contains(sort)) {
            sorting = Sort.by(sort).ascending();
        }
        if (page == null || size == null) {
            return employeeService.getAllEmployees(sorting);
        }
        Pageable paging = PageRequest.of(page, size, sorting);
        return employeeService.getAllEmployees(paging);
    }

    @GetMapping(value = "/by_manager/{managerId}")
    public List<Employee> getEmployeeByManager(
            @PathVariable Long managerId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort
    ) {
        Sort sorting = Sort.unsorted();
        if (sort != null && !sort.isEmpty() && sortParams.contains(sort)) {
            sorting = Sort.by(sort).ascending();
        }
        if (page == null || size == null) {
            return employeeService.getAllEmployeesByManagerId(managerId, sorting);
        }
        Pageable paging = PageRequest.of(page, size, sorting);
        return employeeService.getAllEmployeesByManagerId(managerId, paging);
    }

    @GetMapping(value = "/by_department/{dep}")
    public List<Employee> getEmployeeByDepartment(
            @PathVariable String dep,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort
    ) {
        Sort sorting = Sort.unsorted();
        if (sort != null && !sort.isEmpty() && sortParams.contains(sort)) {
            sorting = Sort.by(sort).ascending();
        }
        if (page == null || size == null) {
            return employeeService.getAllEmployeesByDepartment(dep, sorting);
        }
        Pageable paging = PageRequest.of(page, size, sorting);
        return employeeService.getAllEmployeesByDepartment(dep, paging);
    }
}
