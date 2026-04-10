package com.wangjun.text_proof_platform.modules.proof.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
//计算 SHA-256 哈希的服务类
@Service
public class ProofHashService {
    //为了让文件去做 RFC3161 时间戳
    public byte[] digest(byte[] data) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return messageDigest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }
    //让小段文本，也就是直接存到 MySQL 里面的去做时间戳
    public byte[] digest(String text) {
        return digest(text.getBytes(StandardCharsets.UTF_8));
    }
    //把文件内容算成可读的 SHA-256 十六进制字符串，方便存数据库。
    public String sha256Hex(byte[] data) {
        byte[] digest = digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    //短文本内容的 hash 存到 MySQL
    public String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }
}