package com.wangjun.text_proof_platform.modules.audit.repository;

import com.wangjun.text_proof_platform.modules.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
//提供AuditLog 表的增删改查
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}