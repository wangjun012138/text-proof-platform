package com.wangjun.text_proof_platform.modules.user.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPwdRequest {
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Verification code cannot be blank")
    @Size(min = 6, max = 6, message = "Verification code must be exactly 6 digits")
    @Pattern(regexp = "^[0-9]+$", message = "Verification code must contain only numbers")
    private String code;

    @NotBlank(message = "New password cannot be blank")
    @Size(min = 6, max = 50, message = "Password length must be between 6 and 50 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain both letters and numbers")
    private String newPassword;
}
