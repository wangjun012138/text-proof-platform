package com.wangjun.text_proof_platform.modules.share.repository;

import com.wangjun.text_proof_platform.modules.share.entity.TextProofShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TextProofShareRepository extends JpaRepository<TextProofShare, Long> {
    //查出某个用户发起的所有分享记录，并按创建时间从新到旧排列
    List<TextProofShare> findAllByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);
    //查一条指定 id 的分享记录，但要求这条记录必须属于某个 ownerUsername:自己看自己的
    Optional<TextProofShare> findByIdAndOwnerUsername(Long id, String ownerUsername);
    //查一条指定 id 的分享记录，但要求这条记录的目标接收人是某个用户：看别人的
    Optional<TextProofShare> findByIdAndTargetUsername(Long id, String targetUsername);
    //通过分享 token 查找对应的分享记录
    Optional<TextProofShare> findByShareToken(String shareToken);
    //查数据库里有没有一条分享记录，它的 shareToken 等于这个值
    boolean existsByShareToken(String shareToken);
    //删除某条存证对应的所有分享记录
    void deleteAllByTextProofId(Long textProofId);
}