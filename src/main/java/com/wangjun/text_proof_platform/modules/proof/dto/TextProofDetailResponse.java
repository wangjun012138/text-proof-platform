package com.wangjun.text_proof_platform.modules.proof.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
//详情页返回对象
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextProofDetailResponse {
    private Long id;
    private String ownerUsername;
    private String subject;
    private String contentType;
    private String textContent;
    private String originalFilename;
    private Long fileSize;
    private String mimeType;
    private String contentHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String rfc3161Status;
    private String rfc3161Provider;
    private LocalDateTime rfc3161TimestampAt;
}