package com.wangjun.text_proof_platform.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePwdRequest {
    //@NotBlank:前端参数就能拦住
    //@Column:前端传空值时，Controller层可能不报错，直到保存到数据库才报错
    //DTO 上用 @NotBlank、@Size、@Email
    //Entity 上用 @Column(nullable = false, length = ...)
    @NotBlank(message = "Old password cannot be blank")
    private String oldPassword;

    @NotBlank(message = "New password cannot be blank")
    @Size(min = 6, max = 50, message = "Password length must be between 6 and 50 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain both letters and numbers")
    private String newPassword;
}
