package com.wangjun.text_proof_platform.modules.share.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
//分享创建成功后的返回对象。前端传给后端的请求体格式
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateShareResponse {
    private Long shareId;
    private String shareType;
    private String targetUsername;
    private String shareToken;
    private LocalDateTime expireAt;
}