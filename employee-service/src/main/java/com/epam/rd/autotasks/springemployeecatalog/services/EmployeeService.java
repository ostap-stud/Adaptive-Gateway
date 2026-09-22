package com.epam.rd.autotasks.springemployeecatalog.services;

import com.epam.rd.autotasks.springemployeecatalog.data.DepartmentEntity;
import com.epam.rd.autotasks.springemployeecatalog.data.EmployeeEntity;
import com.epam.rd.autotasks.springemployeecatalog.data.EmployeeRepository;
import com.epam.rd.autotasks.springemployeecatalog.domain.Department;
import com.epam.rd.autotasks.springemployeecatalog.domain.Employee;
import com.epam.rd.autotasks.springemployeecatalog.domain.FullName;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmployeeService {
    private final EmployeeRepository employeeRepository;
    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public Employee save(Employee employee, Employee manager) {
        EmployeeEntity managerEntity = null;
        if (manager != null) {
            managerEntity = new EmployeeEntity(
                    employee.getId(),
                    employee.getFullName().getFirstName(),
                    employee.getFullName().getLastName(),
                    employee.getFullName().getMiddleName(),
                    employee.getPosition(),
                    employee.getHired(),
                    employee.getSalary(),
                    null,
                    null
            );
        }
        EmployeeEntity employeeEntity = new EmployeeEntity(
                employee.getId(),
                employee.getFullName().getFirstName(),
                employee.getFullName().getLastName(),
                employee.getFullName().getMiddleName(),
                employee.getPosition(),
                employee.getHired(),
                employee.getSalary(),
                managerEntity,
                null
        );
        EmployeeEntity saved = employeeRepository.save(employeeEntity);
        return mapToEmployee(saved, manager);
    }

    public Employee getEmployeeById(Long id, Boolean fullChain) {
        EmployeeEntity employeeEntity = employeeRepository.findById(id).orElse(null);
        if (employeeEntity != null) {
            if (fullChain) {
                return getMappedManagersFullChain(employeeEntity);
            }
            EmployeeEntity managerEntity = employeeEntity.getManager();
            Employee manager = managerEntity != null ? mapToEmployee(managerEntity, null) : null;
            return mapToEmployee(employeeEntity, manager);
        }
        return null;
    }

    private Employee getMappedManagersFullChain(EmployeeEntity employeeEntity) {
        if (employeeEntity == null){
            return null;
        }
        return mapToEmployee(employeeEntity, getMappedManagersFullChain(employeeEntity.getManager()));
    }

    public List<Employee> getAllEmployeesByDepartment(String department, Pageable pageable) {
        try {
            long departmentId = Long.parseLong(department);
            return getMappedEmployees(
                    employeeRepository.findAllByDepartmentId(departmentId, pageable)
            );
        }catch (NumberFormatException e) {
            return getMappedEmployees(
                    employeeRepository.findAllByDepartmentName(department, pageable)
            );
        }
    }

    public List<Employee> getAllEmployeesByDepartment(String department, Sort sorting) {
        try {
            long departmentId = Long.parseLong(department);
            return getMappedEmployees(
                    employeeRepository.findAllByDepartmentId(departmentId, sorting)
            );
        }catch (NumberFormatException e) {
            return getMappedEmployees(
                    employeeRepository.findAllByDepartmentName(department, sorting)
            );
        }
    }

    public List<Employee> getAllEmployeesByManagerId(Long managerId, Pageable pageable) {
        return getMappedEmployees(
                employeeRepository.findAllByManagerId(managerId, pageable)
        );
    }

    public List<Employee> getAllEmployeesByManagerId(Long managerId, Sort sorting) {
        return getMappedEmployees(
                employeeRepository.findAllByManagerId(managerId, sorting)
        );
    }

    public List<Employee> getAllEmployees(Pageable pageable) {
        return getMappedEmployees(
                employeeRepository.findAll(pageable)
        );
    }

    public List<Employee> getAllEmployees(Sort sorting) {
        return getMappedEmployees(
                employeeRepository.findAll(sorting)
        );
    }

    private List<Employee> getMappedEmployees(Iterable<EmployeeEntity> employeeEntities) {
        List<Employee> employees = new ArrayList<>();
        for (EmployeeEntity employeeEntity : employeeEntities) {
            EmployeeEntity managerEntity = employeeEntity.getManager();
            Employee manager = managerEntity != null ? mapToEmployee(managerEntity, null) : null;
            Employee employee = mapToEmployee(employeeEntity, manager);
            employees.add(employee);
        }
        return employees;
    }

    private Employee mapToEmployee(EmployeeEntity employeeEntity, Employee manager) {
        FullName employeeName = new FullName(
                employeeEntity.getFirstName(), employeeEntity.getLastName(), employeeEntity.getMiddleName()
        );
        Department department = null;
        DepartmentEntity departmentEntity = employeeEntity.getDepartment();
        department = departmentEntity != null ?
                new Department(departmentEntity.getId(), departmentEntity.getName(), departmentEntity.getLocation()) :
                department;
        return new Employee(
                employeeEntity.getId(), employeeName, employeeEntity.getPosition(), employeeEntity.getHired(),
                employeeEntity.getSalary(), manager, department
        );
    }
}
