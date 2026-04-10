package com.wangjun.text_proof_platform.modules.share.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
//用户请求自己发出的分享列表的返回格式
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextProofShareListItemResponse {
    private Long shareId;
    private Long textProofId;
    private String proofSubject;
    private String proofContentType;
    private String shareType;
    private String targetUsername;
    private String shareToken;
    private LocalDateTime expireAt;
    private boolean revoked;
    private LocalDateTime createdAt;
}