package com.wangjun.text_proof_platform.modules.share.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "biz_text_proof_share")
public class TextProofShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text_proof_id", nullable = false)
    private Long textProofId;

    @Column(name = "owner_username", nullable = false, length = 50)
    private String ownerUsername;

    @Column(name = "share_type", nullable = false, length = 20)
    private String shareType; // USER / TOKEN

    @Column(name = "target_username", length = 50)
    private String targetUsername;

    @Column(name = "share_token", length = 128, unique = true)
    private String shareToken;
    //过期时间
    @Column(name = "expire_at")
    private LocalDateTime expireAt;
    //这条分享是否已被撤销
    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    //这条分享记录最后一次更新时间
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}