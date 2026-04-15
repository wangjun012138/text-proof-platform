package com.wangjun.text_proof_platform.common;
//限流异常类
public class TooManyLoginAttemptsException extends RuntimeException {
    //用户还需要等待多少秒，才能再次尝试登录
    private final int retryAfterSeconds;

    public TooManyLoginAttemptsException(String message, int retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}