package com.wangjun.text_proof_platform.modules.proof.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
//优先使用proof.rfc3161里面的配置，如果没有，才用类里面设置的初始值
@ConfigurationProperties(prefix = "proof.rfc3161")
public class Rfc3161Properties {
    private String mode = "OFF";          // OFF / LOCAL_DEMO / FREETSA
    private String url = "https://freetsa.org/tsr";
    private int timeoutSeconds = 15;
}