package com.wangjun.text_proof_platform.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

//DTO 前后端、或者不同层之间传递数据时，约定好的“数据结构格式

@Data
public class LoginRequest {
    @NotBlank(message = "Account cannot be blank")
    private String account;
    @NotBlank(message = "Password cannot be blank")
    private String password;}
