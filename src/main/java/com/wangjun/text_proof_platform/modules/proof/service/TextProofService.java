package com.wangjun.text_proof_platform.modules.proof.service;

import com.wangjun.text_proof_platform.common.BadRequestException;
import com.wangjun.text_proof_platform.common.ResourceNotFoundException;
import com.wangjun.text_proof_platform.modules.proof.dto.TextProofDetailResponse;
import com.wangjun.text_proof_platform.modules.proof.dto.TextProofListItemResponse;
import com.wangjun.text_proof_platform.modules.proof.entity.TextProof;
import com.wangjun.text_proof_platform.modules.proof.entity.TextProofAudit;
import com.wangjun.text_proof_platform.modules.proof.repository.TextProofAuditRepository;
import com.wangjun.text_proof_platform.modules.proof.repository.TextProofRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.wangjun.text_proof_platform.modules.share.repository.TextProofShareRepository;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TextProofService {

    private final TextProofRepository textProofRepository;
    private final ProofHashService proofHashService;
    private final ProofStorageService proofStorageService;
    private final Rfc3161TimestampService rfc3161TimestampService;
    private final TextProofShareRepository textProofShareRepository;
    private final TextProofAuditRepository textProofAuditRepository;
    public TextProofService(TextProofRepository textProofRepository,
                            ProofHashService proofHashService,
                            ProofStorageService proofStorageService,
                            Rfc3161TimestampService rfc3161TimestampService,
                            TextProofShareRepository textProofShareRepository,
                            TextProofAuditRepository textProofAuditRepository
    ) {
        this.textProofRepository = textProofRepository;
        this.proofHashService = proofHashService;
        this.proofStorageService = proofStorageService;
        this.rfc3161TimestampService = rfc3161TimestampService;
        this.textProofShareRepository = textProofShareRepository;
        this.textProofAuditRepository = textProofAuditRepository;
    }
    //创建短文本存证
    @Transactional
    public Long createTextProof(String subject, String content, String ownerUsername) {
        //构造 TextProof
        TextProof proof = new TextProof();
        proof.setOwnerUsername(ownerUsername);
        proof.setSubject(subject);
        proof.setContentType("TEXT");
        proof.setTextContent(content);
        //计算文件 SHA-256
        byte[] digest = proofHashService.digest(content);
        proof.setContentHash(proofHashService.sha256Hex(content));
        //调时间戳服务
        TimestampResult timestampResult = rfc3161TimestampService.timestamp(digest);
        applyTimestampResult(proof, timestampResult);
        proof.setVersionNo(1);
        textProofRepository.save(proof);
        saveAuditSnapshot(proof, "CREATED");
        return proof.getId();
    }
    //创建文件存证
    @Transactional
    public Long createFileProof(String subject, MultipartFile file, String ownerUsername) throws IOException {
        ProofStorageService.StoredFile storedFile = proofStorageService.store(file);

        TextProof proof = new TextProof();
        proof.setOwnerUsername(ownerUsername);
        proof.setSubject(subject);
        proof.setContentType("FILE");
        proof.setTextContent(null);
        proof.setFilePath(storedFile.getRelativePath());
        proof.setOriginalFilename(storedFile.getOriginalFilename());
        proof.setFileSize(storedFile.getFileSize());
        proof.setMimeType(storedFile.getMimeType());

        byte[] digest = proofHashService.digest(storedFile.getBytes());
        proof.setContentHash(proofHashService.sha256Hex(storedFile.getBytes()));

        TimestampResult timestampResult = rfc3161TimestampService.timestamp(digest);
        applyTimestampResult(proof, timestampResult);

        textProofRepository.save(proof);
        return proof.getId();
    }
    //查当前用户自己的存证列表
    @Transactional(readOnly = true)
    public List<TextProofListItemResponse> listMyProofs(String ownerUsername) {
        return textProofRepository.findAllByOwnerUsernameOrderByCreatedAtDesc(ownerUsername)
                .stream()
                .map(this::toListItem)
                .toList();
    }
    //查某条存证详情
    @Transactional(readOnly = true)
    public TextProofDetailResponse getDetail(Long id, String ownerUsername) {
        TextProof proof = getOwnedProof(id, ownerUsername);
        return toDetail(proof);
    }
    //下载文件存证对应的文件
    @Transactional(readOnly = true)
    public DownloadedFile downloadFile(Long id, String ownerUsername) {
        TextProof proof = getOwnedProof(id, ownerUsername);
        if (!"FILE".equals(proof.getContentType())) {
            throw new BadRequestException("Shared item is not a file");
        }
        if (!StringUtils.hasText(proof.getFilePath())) {
            throw new ResourceNotFoundException("File not found");
        }
        Resource resource = proofStorageService.loadAsResource(proof.getFilePath());
        return new DownloadedFile(
                proof.getOriginalFilename(),
                proof.getMimeType(),
                resource
        );
    }
    //把某条文件存证更新成文本型
    @Transactional
    public Long updateTextProof(Long id, String subject, String content, String ownerUsername) {
        TextProof proof = getOwnedProof(id, ownerUsername);

        String oldFilePath = proof.getFilePath();
        boolean oldWasFile = "FILE".equals(proof.getContentType()) && StringUtils.hasText(oldFilePath);

        proof.setSubject(subject);
        proof.setContentType("TEXT");
        proof.setTextContent(content);
        proof.setFilePath(null);
        proof.setOriginalFilename(null);
        proof.setFileSize(null);
        proof.setMimeType(null);

        byte[] digest = proofHashService.digest(content);
        proof.setContentHash(proofHashService.sha256Hex(content));

        TimestampResult timestampResult = rfc3161TimestampService.timestamp(digest);
        applyTimestampResult(proof, timestampResult);


        textProofRepository.saveAndFlush(proof);

        //存证版本更新
        proof.setVersionNo(proof.getVersionNo() + 1);
        saveAuditSnapshot(proof, "UPDATED");

        if (oldWasFile) {
            proofStorageService.deleteStoredFile(oldFilePath);
        }

        return proof.getId();
    }
    //把某条文本存证更新成文件型
    @Transactional
    public Long updateFileProof(Long id, String subject, MultipartFile file, String ownerUsername) throws IOException {
        TextProof proof = getOwnedProof(id, ownerUsername);
        //先把旧文件信息保存下来。
        String oldFilePath = proof.getFilePath();
        boolean oldWasFile = "FILE".equals(proof.getContentType()) && StringUtils.hasText(oldFilePath);

        // 1. 先尝试存新文件；如果这里失败，旧文件还在，不会破坏原状态
        ProofStorageService.StoredFile storedFile = proofStorageService.store(file);

        try {
            // 2. 更新数据库对象
            proof.setSubject(subject);
            proof.setContentType("FILE");
            proof.setTextContent(null);
            proof.setFilePath(storedFile.getRelativePath());
            proof.setOriginalFilename(storedFile.getOriginalFilename());
            proof.setFileSize(storedFile.getFileSize());
            proof.setMimeType(storedFile.getMimeType());

            byte[] digest = proofHashService.digest(storedFile.getBytes());
            proof.setContentHash(proofHashService.sha256Hex(storedFile.getBytes()));

            TimestampResult timestampResult = rfc3161TimestampService.timestamp(digest);
            applyTimestampResult(proof, timestampResult);

            textProofRepository.saveAndFlush(proof);
            //存证版本更新
            proof.setVersionNo(proof.getVersionNo() + 1);
            saveAuditSnapshot(proof, "UPDATED");
            // 3. 数据库成功后，再删旧文件
            if (oldWasFile) {
                proofStorageService.deleteStoredFile(oldFilePath);
            }

            return proof.getId();

        } catch (Exception e) {
            // 4. 如果数据库更新失败，删掉刚刚新存进去的文件，避免留下垃圾文件
            proofStorageService.deleteStoredFile(storedFile.getRelativePath());
            throw e;
        }
    }
    //删除存证
    @Transactional
    public void deleteProof(Long id, String ownerUsername) {
        TextProof proof = getOwnedProof(id, ownerUsername);

        // 删除前先记录一条删除审计
        saveAuditSnapshot(proof, "DELETED");

        // 先删分享记录，保证原存证删掉后分享自动失效
        textProofShareRepository.deleteAllByTextProofId(id);
        //如果是文件型，会先删磁盘文件，再删数据库记录。
        if ("FILE".equals(proof.getContentType()) && StringUtils.hasText(proof.getFilePath())) {
            proofStorageService.deleteStoredFile(proof.getFilePath());
        }

        textProofRepository.delete(proof);
    }
    //查看历史版本
    @Transactional(readOnly = true)
    public List<TextProofAudit> getProofHistory(Long id, String ownerUsername) {
        // 先确认这条 proof 属于当前用户
        getOwnedProof(id, ownerUsername);

        return textProofAuditRepository
                .findAllByProofIdAndOwnerUsernameOrderByVersionNoAsc(id, ownerUsername);
    }
    //保存审计快照
    private void saveAuditSnapshot(TextProof proof, String action) {
        TextProofAudit audit = new TextProofAudit();
        audit.setProofId(proof.getId());
        audit.setOwnerUsername(proof.getOwnerUsername());
        audit.setSubject(proof.getSubject());
        audit.setContentType(proof.getContentType());
        audit.setTextContent(proof.getTextContent());
        audit.setFilePath(proof.getFilePath());
        audit.setOriginalFilename(proof.getOriginalFilename());
        audit.setFileSize(proof.getFileSize());
        audit.setMimeType(proof.getMimeType());
        audit.setContentHash(proof.getContentHash());
        audit.setVersionNo(proof.getVersionNo());
        audit.setAuditAction(action);
        audit.setProofCreatedAt(proof.getCreatedAt());
        audit.setProofUpdatedAt(proof.getUpdatedAt());
        audit.setAuditedAt(LocalDateTime.now());
        audit.setRfc3161Status(proof.getRfc3161Status());
        audit.setRfc3161Provider(proof.getRfc3161Provider());
        audit.setRfc3161TimestampAt(proof.getRfc3161TimestampAt());

        textProofAuditRepository.save(audit);
    }


    //统一封装“按 id + 当前用户查记录”的逻辑
    private TextProof getOwnedProof(Long id, String ownerUsername) {
        return textProofRepository.findByIdAndOwnerUsername(id, ownerUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Proof record not found"));
    }
    //把时间戳服务返回的结果，写回到 TextProof 实体里。小函数
    private void applyTimestampResult(TextProof proof, TimestampResult timestampResult) {
        proof.setRfc3161Status(timestampResult.getStatus());
        proof.setRfc3161Provider(timestampResult.getProvider());
        proof.setRfc3161Token(timestampResult.getToken());
        proof.setRfc3161TimestampAt(timestampResult.getTimestampAt());
    }
    //返回列表的Entity 转 DTO返回：避免 Controller 直接返回数据库实体。
    private TextProofListItemResponse toListItem(TextProof proof) {
        return new TextProofListItemResponse(
                proof.getId(),
                proof.getSubject(),
                proof.getContentType(),
                proof.getContentHash(),
                proof.getCreatedAt(),
                proof.getRfc3161Status(),
                proof.getOriginalFilename(),
                proof.getFileSize()
        );
    }
    //单条存证的Entity 转 DTO返回：避免 Controller 直接返回数据库实体。
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