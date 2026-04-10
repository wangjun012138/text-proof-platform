package com.wangjun.text_proof_platform.modules.proof.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
//列表页返回对象
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextProofListItemResponse {
    private Long id;
    private String subject;
    private String contentType;
    private String contentHash;
    private LocalDateTime createdAt;
    private String rfc3161Status;
    private String originalFilename;
    private Long fileSize;
}