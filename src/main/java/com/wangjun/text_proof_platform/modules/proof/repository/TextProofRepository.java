package com.wangjun.text_proof_platform.modules.proof.repository;

import com.wangjun.text_proof_platform.modules.proof.entity.TextProof;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
//负责操作 biz_text_proof 表
public interface TextProofRepository extends JpaRepository<TextProof, Long> {
    //查当前用户自己的所有存证，并按创建时间倒序排列
    List<TextProof> findAllByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);
    //查“某条指定 id 的存证”，并且它必须属于当前用户
    Optional<TextProof> findByIdAndOwnerUsername(Long id, String ownerUsername);
}