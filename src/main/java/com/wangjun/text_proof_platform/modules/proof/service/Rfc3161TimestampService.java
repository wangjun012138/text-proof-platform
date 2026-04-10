package com.wangjun.text_proof_platform.modules.proof.service;
//接口：抽象出“时间戳服务”这个能力。service代码随时切换实现，而不改业务层
public interface Rfc3161TimestampService {
    TimestampResult timestamp(byte[] digest);
}