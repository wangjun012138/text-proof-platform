package com.wangjun.text_proof_platform.modules.proof.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
//存证主表对应的实体类
@Data
@Entity
@Table(name = "biz_text_proof")
public class TextProof {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String ownerUsername;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, length = 20)
    private String contentType; // TEXT / FILE

    @Lob//表示这是一个大对象字段（Large Object）
    private String textContent;

    @Column(length = 500)
    private String filePath;

    @Column(length = 255)
    private String originalFilename;

    private Long fileSize;

    @Column(length = 100)
    //文件内容的类型
    private String mimeType;

    @Column(nullable = false, length = 128)
    //内容hash：确认内容是否被改过
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(length = 20)
    //时间戳处理状况
    private String rfc3161Status;

    @Column(length = 100)
    private String rfc3161Provider;

    @Lob
    private String rfc3161Token;

    @Column(name = "rfc3161_timestamp_at")
    private LocalDateTime rfc3161TimestampAt;

    @Column(nullable = false)
    private boolean deleted = false;

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
