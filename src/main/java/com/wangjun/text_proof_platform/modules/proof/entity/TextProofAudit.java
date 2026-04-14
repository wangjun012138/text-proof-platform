package com.wangjun.text_proof_platform.modules.proof.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "biz_text_proof_audit")
public class TextProofAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proof_id", nullable = false)
    private Long proofId;

    @Column(nullable = false, length = 50)
    private String ownerUsername;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, length = 20)
    private String contentType;

    @Lob
    private String textContent;

    @Column(length = 500)
    private String filePath;

    @Column(length = 255)
    private String originalFilename;

    private Long fileSize;

    @Column(length = 100)
    private String mimeType;

    @Column(nullable = false, length = 128)
    private String contentHash;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "audit_action", nullable = false, length = 20)
    private String auditAction; // CREATED / UPDATED / DELETED

    @Column(name = "proof_created_at", nullable = false)
    private LocalDateTime proofCreatedAt;

    @Column(name = "proof_updated_at", nullable = false)
    private LocalDateTime proofUpdatedAt;

    @Column(name = "audited_at", nullable = false)
    private LocalDateTime auditedAt;

    @Column(length = 20)
    private String rfc3161Status;

    @Column(length = 100)
    private String rfc3161Provider;

    @Column(name = "rfc3161_timestamp_at")
    private LocalDateTime rfc3161TimestampAt;
}
