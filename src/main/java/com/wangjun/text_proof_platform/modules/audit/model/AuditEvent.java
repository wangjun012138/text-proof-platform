package com.wangjun.text_proof_platform.modules.audit.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
//线程之间通信的“消息”
public class AuditEvent {

    private String username;
    private String action;
    private String targetType;
    private Long targetId;
    private String result;
    private String ip;
    private String message;
    private LocalDateTime eventTime;
}