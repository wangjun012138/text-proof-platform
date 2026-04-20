package com.wangjun.text_proof_platform.modules.user.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "user_verification_code")
public class VerificationCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // 哪个邮箱收到的验证码
    @Column(nullable = false,length = 100)
    private String email;
    // 6位验证码
    @Column(nullable = false,length = 10)
    private String code;
    // 过期时间
    @Column(name = "expire_at",nullable = false)
    private LocalDateTime expireAt;
    //验证码是否被使用
    @Column(nullable = false)
    private boolean used = false;

    // 验证码已经输错的次数
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "create_at",nullable = false)
    private LocalDateTime createdAt;
    @PrePersist
    public void prePersist(){
        if(createdAt == null){
            createdAt = LocalDateTime.now();
        }
    }
}
