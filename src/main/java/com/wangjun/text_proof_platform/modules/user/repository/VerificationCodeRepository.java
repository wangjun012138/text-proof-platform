package com.wangjun.text_proof_platform.modules.user.repository;

import com.wangjun.text_proof_platform.modules.user.entity.VerificationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface VerificationCodeRepository  extends JpaRepository<VerificationCode, Long> {

    // 取某邮箱最新的一条验证码
    //Desc是降序，Asc是升序
    Optional<VerificationCode> findTopByEmailOrderByIdDesc(String email);

    //加锁读取某邮箱最新的一条验证码，注册时使用，悲观锁
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VerificationCode> findTopByEmailAndUsedFalseOrderByIdDesc(String email);

}
