package com.wangjun.text_proof_platform.common;

//当系统里“某个资源没找到”时，用这个异常来明确表达错误语义。
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
