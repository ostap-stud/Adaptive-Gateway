package com.epam.rd.autotasks.springemployeecatalog.data;

import com.epam.rd.autotasks.springemployeecatalog.domain.Position;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "EMPLOYEE")
public class EmployeeEntity {
    @Id
    private Long id;
    @Column(name = "FIRSTNAME")
    private String firstName;
    @Column(name = "LASTNAME")
    private String lastName;
    @Column(name = "MIDDLENAME")
    private String middleName;
    @Enumerated(EnumType.STRING)
    private Position position;
    @Column(name = "HIREDATE")
    private LocalDate hired;
    private BigDecimal salary;
    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "MANAGER")
    private EmployeeEntity manager;
    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "DEPARTMENT")
    private DepartmentEntity department;

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public Position getPosition() {
        return position;
    }

    public LocalDate getHired() {
        return hired;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public EmployeeEntity getManager() {
        return manager;
    }

    public DepartmentEntity getDepartment() {
        return department;
    }

    public EmployeeEntity() {
    }

    public EmployeeEntity(Long id, String firstName, String lastName, String middleName, Position position, LocalDate hired, BigDecimal salary, EmployeeEntity manager, DepartmentEntity department) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.middleName = middleName;
        this.position = position;
        this.hired = hired;
        this.salary = salary;
        this.manager = manager;
        this.department = department;
    }
}
