package com.wangjun.text_proof_platform.modules.share.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
//创建分享成功后，统一返回给前端的响应数据格式。
@Data
public class CreateUserShareRequest {

    @NotNull(message = "Text proof ID cannot be null")
    private Long textProofId;

    @NotBlank(message = "Target username cannot be blank")
    private String targetUsername;

    @NotNull(message = "Expiration days cannot be null")
    @Min(value = 1, message = "Expiration days must be at least 1")
    private Integer expireDays;
}