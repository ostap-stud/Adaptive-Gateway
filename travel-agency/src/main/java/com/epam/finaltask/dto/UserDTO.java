package com.epam.finaltask.dto;

import java.util.List;

import com.epam.finaltask.model.Voucher;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {

	private String id;

	private String username;

	private String password;

	private String role;

	private List<Voucher> vouchers;

	private String email;

	private String phoneNumber;

	private Double balance;

	private boolean active;
	
}
