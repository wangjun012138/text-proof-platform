package com.wangjun.text_proof_platform.modules.proof.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
//优先使用application.yml里面的proof.storage.*里面的配置，如果没有，再用初始值= "./runtime/proof-files";
@ConfigurationProperties(prefix = "proof.storage")
public class ProofStorageProperties {
    private String root = "./runtime/proof-files";
}