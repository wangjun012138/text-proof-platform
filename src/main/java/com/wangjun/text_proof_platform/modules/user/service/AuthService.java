package com.wangjun.text_proof_platform.modules.user.service;

import com.wangjun.text_proof_platform.common.BadRequestException;
import com.wangjun.text_proof_platform.modules.user.entity.User;
import com.wangjun.text_proof_platform.modules.user.entity.VerificationCode;
import com.wangjun.text_proof_platform.modules.user.repository.UserRepository;
import com.wangjun.text_proof_platform.modules.user.repository.VerificationCodeRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

//核心业务逻辑层，处理应用业务
@Service
public class AuthService {
    private final UserRepository userRepository ;
    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionService userSessionService;
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
        VerificationCode verificationCode = verificationCodeRepository.findTopByEmailAndUsedFalseOrderByIdDesc(email)
                .orElseThrow(() -> new BadRequestException("Verification code is invalid or does not exist"));
        if(verificationCode.getExpireAt().isBefore(LocalDateTime.now())){
            throw new BadRequestException("Verification code has expired");
        }
        if(!verificationCode.getCode().equals(code)){
            throw new BadRequestException("Incorrect verification code");
        }
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
        verificationCode.setUsed(true);
        verificationCodeRepository.save(verificationCode);
    }
    public void sendCode(String email){
        String code = String.valueOf(new Random().nextInt(900000)+100000);
        //每次发送验证码都必须有一个新的对象，所以必须 new。
        VerificationCode verificationCode = new VerificationCode();
        verificationCode.setEmail(email);
        verificationCode.setCode(code);
        verificationCode.setExpireAt(LocalDateTime.now().plusMinutes(5));
        verificationCode.setUsed(false);
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
    @Transactional
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
        VerificationCode verificationCode = verificationCodeRepository
                .findTopByEmailAndUsedFalseOrderByIdDesc(email)
                .orElseThrow(() -> new BadRequestException("Verification code is invalid or does not exist"));

        if (verificationCode.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Verification code has expired");
        }

        if (!verificationCode.getCode().equals(code)) {
            throw new BadRequestException("Incorrect verification code");
        }

        User user = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new BadRequestException("Email is not registered"));

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BadRequestException("New password cannot be the same as the old password");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        //现在就把更新同步到数据库，而不是等事务快结束时再统一 flush。
        userRepository.saveAndFlush(user);
        verificationCode.setUsed(true);
        verificationCodeRepository.save(verificationCode);
        userSessionService.expireSessions(user.getUsername());
    }
}
