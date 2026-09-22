package com.epam.finaltask.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String username;

    private String password;

	@Enumerated(EnumType.STRING)
    private Role role;

	@OneToMany(mappedBy = "user")
    private List<Voucher> vouchers;

    private String email;

    private String phoneNumber;

    private BigDecimal balance;

    private boolean active;

}