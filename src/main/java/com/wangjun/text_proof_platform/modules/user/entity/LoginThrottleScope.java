package com.wangjun.text_proof_platform.modules.user.entity;
//同一个账号连续输错很多次
//同一个 IP 连续打很多次登录请求
public enum LoginThrottleScope {
    ACCOUNT,
    IP
}