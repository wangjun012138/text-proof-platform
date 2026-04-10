package com.wangjun.text_proof_platform.modules.share.dto;

import com.wangjun.text_proof_platform.modules.proof.dto.TextProofDetailResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
//任何通过分享入口成功访问到这条分享时，返回的详情格式
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharedTextProofDetailResponse {
    private Long shareId;
    private String shareType;
    private String targetUsername;
    private String shareToken;
    private LocalDateTime expireAt;
    private boolean revoked;
    private TextProofDetailResponse proof;
}