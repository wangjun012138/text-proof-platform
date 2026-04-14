package com.wangjun.text_proof_platform.modules.proof.service;

import com.wangjun.text_proof_platform.common.BadRequestException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProofStorageService {
    //文件存储位置
    private final ProofStorageProperties properties;

    public ProofStorageService(ProofStorageProperties properties) {
        this.properties = properties;
    }
    //返回结果对象:文件存完以后，我把这些信息返回给上层
    @Data
    @AllArgsConstructor
    public static class StoredFile {
        private String relativePath;//相对路径/文件名
        private String originalFilename;
        private Long fileSize;
        private String mimeType;
        private byte[] bytes;
    }

    public StoredFile store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File path cannot be empty");
        }

        Path root = getRootPath();
        Files.createDirectories(root);
        //开始清理路径
        //名字为null，设置默认值
        String originalFilename = StringUtils.cleanPath(
                Objects.requireNonNullElse(file.getOriginalFilename(), "upload.bin")
        );
        //提取文件后缀，保留格式
        String extension = "";
        int dot = originalFilename.lastIndexOf('.');
        if (dot >= 0) {
            extension = originalFilename.substring(dot);
        }
        //生成随机文件名:避免文件名冲突，避免直接使用原文件名
        String storedName = UUID.randomUUID().toString().replace("-", "") + extension;
        //拼出目标路径并规范化
        Path target = root.resolve(storedName).normalize();
        //防止路径穿越,防止攻击者想构造路径跳出去。
        if (!target.startsWith(root)) {
            throw new BadRequestException("Invalid file path");
        }
        //文件读取到内存中，方便计算哈希
        byte[] bytes = file.getBytes();
        //写入磁盘
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW);

        return new StoredFile(
                storedName,
                originalFilename,
                file.getSize(),
                file.getContentType(),
                bytes
        );
    }
    //删除文件
    public void deleteStoredFile(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return;
        }
        try {
            Path root = getRootPath();
            Path filePath = root.resolve(relativePath).normalize();
            if (filePath.startsWith(root)) {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException ignored) {
        }
    }
    //加载文件资源
    public Resource loadAsResource(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new BadRequestException("File path cannot be empty");
        }
        try {
            Path root = getRootPath();
            Path filePath = root.resolve(relativePath).normalize();
            if (!filePath.startsWith(root)) {
                throw new BadRequestException("Invalid file path");
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BadRequestException("File does not exist or is unreadable");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new BadRequestException("File path error");
        }
    }

    private Path getRootPath() {
        return Paths.get(properties.getRoot()).toAbsolutePath().normalize();
    }
}