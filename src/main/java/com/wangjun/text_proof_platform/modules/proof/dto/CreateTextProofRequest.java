package com.wangjun.text_proof_platform.modules.proof.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
//用来接收“创建短文本存证”的请求
@Data
public class CreateTextProofRequest {

    @NotBlank(message = "Subject cannot be blank")
    @Size(max = 255, message = "Subject length cannot exceed 255 characters")
    private String subject;

    @NotBlank(message = "Text content cannot be blank")
    @Size(max = 10000, message = "Short text length cannot exceed 10000 characters")
    private String content;
}