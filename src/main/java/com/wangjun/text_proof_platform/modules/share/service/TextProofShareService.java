package com.wangjun.text_proof_platform.modules.share.service;

import com.wangjun.text_proof_platform.common.BadRequestException;
import com.wangjun.text_proof_platform.common.ResourceNotFoundException;
import com.wangjun.text_proof_platform.modules.proof.dto.TextProofDetailResponse;
import com.wangjun.text_proof_platform.modules.proof.entity.TextProof;
import com.wangjun.text_proof_platform.modules.proof.repository.TextProofRepository;
import com.wangjun.text_proof_platform.modules.proof.service.ProofStorageService;
import com.wangjun.text_proof_platform.modules.share.dto.CreateShareResponse;
import com.wangjun.text_proof_platform.modules.share.dto.SharedTextProofDetailResponse;
import com.wangjun.text_proof_platform.modules.share.dto.TextProofShareListItemResponse;
import com.wangjun.text_proof_platform.modules.share.entity.TextProofShare;
import com.wangjun.text_proof_platform.modules.share.repository.TextProofShareRepository;
import com.wangjun.text_proof_platform.modules.user.repository.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TextProofShareService {
    //操作分享表
    private final TextProofShareRepository textProofShareRepository;
    //操作原始存证表
    private final TextProofRepository textProofRepository;
    //查用户是否存在
    private final UserRepository userRepository;
    //读磁盘文件
    private final ProofStorageService proofStorageService;

    public TextProofShareService(TextProofShareRepository textProofShareRepository,
                                 TextProofRepository textProofRepository,
                                 UserRepository userRepository,
                                 ProofStorageService proofStorageService) {
        this.textProofShareRepository = textProofShareRepository;
        this.textProofRepository = textProofRepository;
        this.userRepository = userRepository;
        this.proofStorageService = proofStorageService;
    }
    //当前登录用户，把某条存证分享给另一个指定用户。
    @Transactional
    public CreateShareResponse createUserShare(Long textProofId,
                                               String targetUsername,
                                               Integer expireDays,
                                               String ownerUsername) {
        TextProof proof = textProofRepository.findByIdAndOwnerUsername(textProofId, ownerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Text proof not found"));

        if (!userRepository.existsByUsername(targetUsername)) {
            throw new BadRequestException("Target user not found");
        }
        //构造一条分享记录share
        TextProofShare share = new TextProofShare();
        share.setTextProofId(proof.getId());
        share.setOwnerUsername(ownerUsername);
        share.setShareType("USER");
        share.setTargetUsername(targetUsername);
        share.setShareToken(null);
        share.setExpireAt(LocalDateTime.now().plusDays(expireDays));

        textProofShareRepository.save(share);

        return new CreateShareResponse(
                share.getId(),
                share.getShareType(),
                share.getTargetUsername(),
                share.getShareToken(),
                share.getExpireAt()
        );
    }
    //当前登录用户，为某条存证生成一个 token 分享链接。
    @Transactional
    public CreateShareResponse createTokenShare(Long textProofId,
                                                Integer expireDays,
                                                String ownerUsername) {
        TextProof proof = textProofRepository.findByIdAndOwnerUsername(textProofId, ownerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Proof record does not exist"));

        TextProofShare share = new TextProofShare();
        share.setTextProofId(proof.getId());
        share.setOwnerUsername(ownerUsername);
        share.setShareType("TOKEN");
        share.setTargetUsername(null);
        //必须用随机值，并且检查数据库里没重复。
        share.setShareToken(generateUniqueToken());
        share.setExpireAt(LocalDateTime.now().plusDays(expireDays));

        textProofShareRepository.save(share);

        return new CreateShareResponse(
                share.getId(),
                share.getShareType(),
                share.getTargetUsername(),
                share.getShareToken(),
                share.getExpireAt()
        );
    }
    //查看“我发出的所有分享”。
    @Transactional(readOnly = true)
    public List<TextProofShareListItemResponse> listMyShares(String ownerUsername) {
        return textProofShareRepository.findAllByOwnerUsernameOrderByCreatedAtDesc(ownerUsername)
                .stream()
                .map(share -> {
                    //前端页面不会显示shareId，shareType等信息，所以需要映射到这条分享对应的是哪条存证，主题，文本还是文件等。
                    TextProof proof = textProofRepository.findById(share.getTextProofId()).orElse(null);
                    String subject = proof != null ? proof.getSubject() : "[Original proof deleted]";
                    String contentType = proof != null ? proof.getContentType() : null;

                    return new TextProofShareListItemResponse(
                            share.getId(),
                            share.getTextProofId(),
                            subject,
                            contentType,
                            share.getShareType(),
                            share.getTargetUsername(),
                            share.getShareToken(),
                            share.getExpireAt(),
                            share.isRevoked(),
                            share.getCreatedAt()
                    );
                })
                .toList();
    }
    //撤销一条我自己发出的分享。
    @Transactional
    public void revokeShare(Long shareId, String ownerUsername) {
        TextProofShare share = textProofShareRepository.findByIdAndOwnerUsername(shareId, ownerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Share record not found"));

        share.setRevoked(true);
        textProofShareRepository.save(share);
    }
    //用于“按用户分享”的访问。只读事务
    @Transactional(readOnly = true)
    public SharedTextProofDetailResponse getUserSharedProof(Long shareId, String currentUsername) {
        //先把访问资格全部校验完
        TextProofShare share = validateUserShareAccess(shareId, currentUsername);
        //加载原始存证
        TextProof proof = loadProofForShare(share);

        return new SharedTextProofDetailResponse(
                share.getId(),
                share.getShareType(),
                share.getTargetUsername(),
                share.getShareToken(),
                share.getExpireAt(),
                share.isRevoked(),
                //原始存证详情
                toDetail(proof)
        );
    }
    //根据token查找分享记录
    @Transactional(readOnly = true)
    public SharedTextProofDetailResponse getTokenSharedProof(String token) {
        TextProofShare share = validateTokenShareAccess(token);
        TextProof proof = loadProofForShare(share);

        return new SharedTextProofDetailResponse(
                share.getId(),
                share.getShareType(),
                share.getTargetUsername(),
                share.getShareToken(),
                share.getExpireAt(),
                share.isRevoked(),
                toDetail(proof)
        );
    }
    //给“按用户分享”的文件下载用
    @Transactional(readOnly = true)
    public DownloadedFile downloadUserSharedFile(Long shareId, String currentUsername) {
        //先校验分享访问资格
        TextProofShare share = validateUserShareAccess(shareId, currentUsername);
        //再加载原存证
        TextProof proof = loadProofForShare(share);
        //最后调用 buildDownloadedFile(proof)
        return buildDownloadedFile(proof);
    }
    //给“token 分享”的文件下载用
    @Transactional(readOnly = true)
    public DownloadedFile downloadTokenSharedFile(String token) {
        TextProofShare share = validateTokenShareAccess(token);
        TextProof proof = loadProofForShare(share);
        return buildDownloadedFile(proof);
    }
    //用户分享访问校验器
    private TextProofShare validateUserShareAccess(Long shareId, String currentUsername) {
        //分享记录存在
        TextProofShare share = textProofShareRepository.findById(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("Share record not found"));
        //分享类型必须是 USER 类型
        if (!"USER".equals(share.getShareType())) {
            throw new ResourceNotFoundException("Share record not found");
        }
        //当前用户必须等于 targetUsername
        if (!currentUsername.equals(share.getTargetUsername())) {
            throw new ResourceNotFoundException("Share record not found");
        }
        //校验这条分享是否过期
        validateActive(share);
        return share;
    }
    //token 版本的访问校验器
    private TextProofShare validateTokenShareAccess(String token) {
        TextProofShare share = textProofShareRepository.findByShareToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Share record not found"));

        if (!"TOKEN".equals(share.getShareType())) {
            throw new ResourceNotFoundException("Share record not found");
        }

        validateActive(share);
        return share;
    }
    //统一校验当前分享是否可用
    private void validateActive(TextProofShare share) {
        if (share.isRevoked()) {
            throw new BadRequestException("Share revoked");
        }

        if (share.getExpireAt() != null && share.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Share expired");
        }
    }
    //加载原始存证:原始存证不存在，认定分享已失效
    private TextProof loadProofForShare(TextProofShare share) {
        TextProof proof = textProofRepository.findById(share.getTextProofId()).orElse(null);
        if (proof == null) {
            throw new ResourceNotFoundException("Share invalid");
        }
        return proof;
    }
    //把原始存证,转成可下载文件。
    private DownloadedFile buildDownloadedFile(TextProof proof) {
        //确认类型
        if (!"FILE".equals(proof.getContentType())) {
            throw new BadRequestException("Shared item is not a file");
        }
        //确认文件路径存在
        if (!StringUtils.hasText(proof.getFilePath())) {
            throw new ResourceNotFoundException("File not found");
        }
        //真正加载磁盘文件
        Resource resource = proofStorageService.loadAsResource(proof.getFilePath());
        return new DownloadedFile(
                proof.getOriginalFilename(),
                proof.getMimeType(),
                resource
        );
    }
    //生成一个数据库里不存在的 token
    private String generateUniqueToken() {
        String token;
        do {
            token = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");
        } while (textProofShareRepository.existsByShareToken(token));
        return token;
    }
    //返回原始存证的全部信息
    private TextProofDetailResponse toDetail(TextProof proof) {
        return new TextProofDetailResponse(
                proof.getId(),
                proof.getOwnerUsername(),
                proof.getSubject(),
                proof.getContentType(),
                proof.getTextContent(),
                proof.getOriginalFilename(),
                proof.getFileSize(),
                proof.getMimeType(),
                proof.getContentHash(),
                proof.getCreatedAt(),
                proof.getUpdatedAt(),
                proof.getRfc3161Status(),
                proof.getRfc3161Provider(),
                proof.getRfc3161TimestampAt()
        );
    }
    //把原文件名、文件类型、文件本体这三个相关信息封装成一个新的对象，方便方法一次性返回。
    public record DownloadedFile(String originalFilename, String mimeType, Resource resource) {
    }
}