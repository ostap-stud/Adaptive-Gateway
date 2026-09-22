package com.epam.rd.autotasks.springemployeecatalog.data;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.List;

@Entity
@Table(name = "DEPARTMENT")
public class DepartmentEntity {
    @Id
    private Long id;
    private String name;
    private String location;
    @OneToMany
    private List<EmployeeEntity> employees;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public List<EmployeeEntity> getEmployees() {
        return employees;
    }
}
