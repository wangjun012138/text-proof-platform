package com.wangjun.text_proof_platform.modules.share.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

//通过token分享:前端传给后端的请求体格式
@Data
public class CreateTokenShareRequest {
    @NotNull(message = "Text proof ID cannot be null")
    private Long textProofId;

    @NotNull(message = "Expiration days cannot be null")
    @Min(value = 1, message = "Expiration days must be at least 1")
    private Integer expireDays;
}