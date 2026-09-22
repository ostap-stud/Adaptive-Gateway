package com.epam.finaltask.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignUpRequest {
    @NotBlank(message = "Has to be not blank")
    @Size(min = 3, max = 20, message = "From 3 to 20 characters")
    private String username;
    @Email(message = "Email is not valid")
    private String email;
    @Pattern(
            regexp = "^(\\+\\d{1,2}\\s)?\\(?\\d{3}\\)?[\\s.-]\\d{3}[\\s.-]\\d{4}$",
            message = "Phone number doesn't match any pattern"
    )
    private String phoneNumber;
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[#$@!%&*?])[A-Za-z\\d#$@!%&*?]{8,}$",
            message = "8 - 20 characters | 1+ uppercase letter | 1+ lowercase letter | 1+ digit | 1+ special | No space, tab\n"
    )
    private String password;
}
