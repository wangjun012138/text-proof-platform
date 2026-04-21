package com.wangjun.text_proof_platform.modules.audit.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "biz_audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 操作用户
    @Column(length = 100)
    private String username;

    // 操作类型，例如 PROOF_CREATE、PROOF_DELETE、SHARE_CREATE
    @Column(nullable = false, length = 50)
    private String action;

    // 业务对象类型，例如 PROOF、SHARE、LOGIN
    @Column(nullable = false, length = 50)
    private String targetType;

    // 业务对象 ID
    private Long targetId;

    // 操作结果：SUCCESS / FAIL
    @Column(nullable = false, length = 20)
    private String result;

    // IP 地址
    @Column(length = 100)
    private String ip;

    // 备注
    @Column(length = 500)
    private String message;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}