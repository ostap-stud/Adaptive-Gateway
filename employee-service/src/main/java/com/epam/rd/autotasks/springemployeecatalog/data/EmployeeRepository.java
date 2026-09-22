package com.epam.rd.autotasks.springemployeecatalog.data;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<EmployeeEntity, Long> {
    List<EmployeeEntity> findAllByManagerId(Long managerId, Pageable pageable);
    List<EmployeeEntity> findAllByManagerId(Long managerId, Sort sorting);
    List<EmployeeEntity> findAllByDepartmentId(Long departmentId, Pageable pageable);
    List<EmployeeEntity> findAllByDepartmentId(Long departmentId, Sort sorting);
    List<EmployeeEntity> findAllByDepartmentName(String departmentName, Pageable pageable);
    List<EmployeeEntity> findAllByDepartmentName(String departmentName, Sort sorting);
}
