package com.wangjun.text_proof_platform.modules.proof.repository;

import com.wangjun.text_proof_platform.modules.proof.entity.TextProofAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TextProofAuditRepository extends JpaRepository<TextProofAudit, Long> {
    //根据用户名字和存证id去查找
    List<TextProofAudit> findAllByProofIdAndOwnerUsernameOrderByVersionNoAsc(Long proofId, String ownerUsername);
}