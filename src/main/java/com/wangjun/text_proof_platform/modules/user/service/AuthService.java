package com.wangjun.text_proof_platform.modules.user.service;

import com.wangjun.text_proof_platform.common.BadRequestException;
import com.wangjun.text_proof_platform.common.TooManyLoginAttemptsException;
import com.wangjun.text_proof_platform.modules.user.entity.User;
import com.wangjun.text_proof_platform.modules.user.entity.VerificationCode;
import com.wangjun.text_proof_platform.modules.user.repository.UserRepository;
import com.wangjun.text_proof_platform.modules.user.repository.VerificationCodeRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

//核心业务逻辑层，处理应用业务
@Service
public class AuthService {
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    private final UserRepository userRepository ;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionService userSessionService;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    public AuthService(UserRepository userRepository,
                       VerificationCodeRepository verificationCodeRepository,
                       PasswordEncoder passwordEncoder,
                       UserSessionService userSessionService) {
        this.userRepository = userRepository;
        this.verificationCodeRepository = verificationCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.userSessionService = userSessionService;
    }
    //注册
    //@Transactional:Spring 提供的事务管理机制
    @Transactional
    public void register(String email,String username,String password,String code){
        verifyCodeOrThrow(email, code);
        boolean duplicatedIdentity = userRepository.existsByEmail(email)
                ||userRepository.existsByUsername(username)
                ||userRepository.existsByEmail(username)
                ||userRepository.existsByUsername(email);
        if(duplicatedIdentity){
            throw new BadRequestException("Registration failed: Email or username already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
        markLatestCodeUsed(email);
    }
    public void sendCode(String email){
        String code = String.valueOf(SECURE_RANDOM.nextInt(900000) + 100000);
        //每次发送验证码都必须有一个新的对象，所以必须 new。
        VerificationCode verificationCode = new VerificationCode();
        verificationCode.setEmail(email);
        verificationCode.setCode(code);
        verificationCode.setExpireAt(LocalDateTime.now().plusMinutes(5));
        verificationCode.setUsed(false);
        verificationCode.setAttemptCount(0);
        verificationCodeRepository.save(verificationCode);
        System.out.println(">>> 验证码发送到邮箱 [" + email + "]，验证码是：" + code);
    }
    //登录
    public String normalizeAccount(String account){
        if(!StringUtils.hasText(account)){
            return "";
        }
        return account.trim();
    }

    public User login(String account, String password){
        String normalizedAccount = normalizeAccount(account);
        Optional<User> userOpt;
        if(normalizedAccount.contains("@")){
            userOpt = userRepository.findByEmail(normalizedAccount);
            if(userOpt.isEmpty()){
                userOpt = userRepository.findByUsername(normalizedAccount);
            }
        }else{
            userOpt = userRepository.findByUsername(normalizedAccount);
            if(userOpt.isEmpty()){
                userOpt = userRepository.findByEmail(normalizedAccount);
            }
        }
        if(userOpt.isEmpty()){
            throw new BadCredentialsException("Login failed: Incorrect account or password");
        }
        User user = userOpt.get();
        if(!passwordEncoder.matches(password,user.getPassword())){
            throw new BadCredentialsException("Login failed: Incorrect account or password");
        }
        return user;
    }

    //修改密码
    @Transactional(noRollbackFor = {
            BadRequestException.class,
            TooManyLoginAttemptsException.class
    })
    public  void changePassword(String username,String oldPassword,String newPassword){
        User user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new BadRequestException("User not found"));
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BadRequestException("Incorrect old password");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BadRequestException("New password cannot be the same as the old password");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        userSessionService.expireSessions(user.getUsername());
    }



    //忘记密码
    @Transactional
    public void resetPassword(String email,String code,String newPassword){

        //BadRequestException表示这次请求有问题，属于客户端输入错误或业务条件不满足，应该返回 400。
        verifyCodeOrThrow(email, code);

        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new BadRequestException("Email is not registered"));

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BadRequestException("New password cannot be the same as the old password");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        //现在就把更新同步到数据库，而不是等事务快结束时再统一 flush。
        userRepository.saveAndFlush(user);
        markLatestCodeUsed(email);
        userSessionService.expireSessions(user.getUsername());
    }
    /**
     * 校验验证码。
     * 关键点：
     * 1. 找到该邮箱最新一条未使用验证码；
     * 2. 过期则拒绝；
     * 3. 超过最大尝试次数则拒绝；
     * 4. 验证码错误时 attemptCount + 1；
     * 5. 达到最大次数后返回 429；
     * 6. 验证码正确时不在这里标记 used，由具体业务成功后再标记 used。
     */
    private void verifyCodeOrThrow(String email, String inputCode) {
        VerificationCode verificationCode = verificationCodeRepository
                .findTopByEmailAndUsedFalseOrderByIdDesc(email)
                .orElseThrow(() -> new BadRequestException("Verification code is invalid or does not exist"));

        LocalDateTime now = LocalDateTime.now();
        //过期
        if (verificationCode.getExpireAt().isBefore(now)) {
            verificationCode.setUsed(true);
            verificationCodeRepository.saveAndFlush(verificationCode);
            throw new BadRequestException("Verification code has expired");
        }
        //超过最大次数
        if (verificationCode.getAttemptCount() >= MAX_VERIFICATION_ATTEMPTS) {
            throwTooManyVerificationAttempts(verificationCode);
        }
        //不相等：错误次数+1
        if (!verificationCode.getCode().equals(inputCode)) {
            int nextAttemptCount = verificationCode.getAttemptCount() + 1;
            verificationCode.setAttemptCount(nextAttemptCount);
            verificationCodeRepository.saveAndFlush(verificationCode);
            //
            if (nextAttemptCount >= MAX_VERIFICATION_ATTEMPTS) {
                throwTooManyVerificationAttempts(verificationCode);
            }

            throw new BadRequestException("Incorrect verification code");
        }
    }

    /**
     * 业务真正成功后，才把验证码标记为 used。
     * 原因：
     * 如果验证码正确，但后续注册失败、重置密码失败，
     * 不应该提前消费验证码。
     */
    private void markLatestCodeUsed(String email) {
        VerificationCode verificationCode = verificationCodeRepository
                .findTopByEmailAndUsedFalseOrderByIdDesc(email)
                .orElseThrow(() -> new BadRequestException("Verification code is invalid or does not exist"));

        verificationCode.setUsed(true);
        verificationCodeRepository.saveAndFlush(verificationCode);
    }
    //把“验证码尝试次数过多”这个场景，统一包装成一个带重试时间的异常抛出去。
    private void throwTooManyVerificationAttempts(VerificationCode verificationCode) {
        int retryAfterSeconds = calculateRetryAfterSeconds(verificationCode);
        throw new TooManyLoginAttemptsException(
                "Too many verification code attempts. Please request a new code later.",
                retryAfterSeconds
        );
    }
    //计算从现在开始，到这条验证码过期为止，还剩多少秒。
    private int calculateRetryAfterSeconds(VerificationCode verificationCode) {
        long seconds = Duration.between(LocalDateTime.now(), verificationCode.getExpireAt()).getSeconds();
        return (int) Math.max(seconds, 1);
    }
}
