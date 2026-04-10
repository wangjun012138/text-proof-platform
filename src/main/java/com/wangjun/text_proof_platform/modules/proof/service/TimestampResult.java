package com.wangjun.text_proof_platform.modules.proof.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimestampResult {
    private String status;          // NONE / STAMPED / FAILED
    private String provider;
    private String token;
    private LocalDateTime timestampAt;
    private String message;
    //表示没打时间戳
    public static TimestampResult none() {
        return new TimestampResult("NONE", "OFF", null, null, "RFC3161 disabled");
    }
    //表示成功打上时间戳
    public static TimestampResult stamped(String provider, String token, LocalDateTime timestampAt) {
        return new TimestampResult("STAMPED", provider, token, timestampAt, "OK");
    }
    //表示打失败了
    public static TimestampResult failed(String provider, String message) {
        return new TimestampResult("FAILED", provider, null, null, message);
    }
}