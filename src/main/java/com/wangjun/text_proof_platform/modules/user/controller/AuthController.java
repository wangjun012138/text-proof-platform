package com.wangjun.text_proof_platform.modules.user.controller;

import com.wangjun.text_proof_platform.common.ApiResponse;
import com.wangjun.text_proof_platform.modules.user.dto.ChangePwdRequest;
import com.wangjun.text_proof_platform.modules.user.dto.LoginRequest;
import com.wangjun.text_proof_platform.modules.user.dto.RegisterRequest;
import com.wangjun.text_proof_platform.modules.user.dto.ResetPwdRequest;
import com.wangjun.text_proof_platform.modules.user.entity.User;
import com.wangjun.text_proof_platform.modules.user.service.AuthService;
import com.wangjun.text_proof_platform.modules.user.service.LoginThrottleService;
import com.wangjun.text_proof_platform.modules.user.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

//Controller 是接口入口层，负责接收 HTTP 请求、调用 Service、返回 HTTP 响应。
@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UserSessionService userSessionService;
    private final SecurityContextRepository securityContextRepository;
    private final LoginThrottleService loginThrottleService;


    public AuthController(AuthService authService,
                          UserSessionService userSessionService,
                          SecurityContextRepository securityContextRepository,
                          LoginThrottleService loginThrottleService
    ) {
        this.authService = authService;
        this.userSessionService = userSessionService;
        this.securityContextRepository = securityContextRepository;
        this.loginThrottleService = loginThrottleService;
    }

    @GetMapping("/csrf")
    public ApiResponse<Map<String,String>> csrf(CsrfToken token){
        Map<String,String> data = new HashMap<>();
        data.put("headerName",token.getHeaderName());
        data.put("parameterName",token.getParameterName());
        data.put("token",token.getToken());
        return ApiResponse.success("CSRF token generated",data);
    }


    @PostMapping("/login")
    public ApiResponse<Long> login(@RequestBody @Valid LoginRequest req,
                                   //和哪次请求、哪个 session 关联
                                   HttpServletRequest request,
                                   //response:如果要回写 cookie / session 信息，往哪里写
                                   HttpServletResponse response) {
        //标准化用户名
        String normalizedAccount = authService.normalizeAccount(req.getAccount());
        //提取ip地址
        String clientIp = loginThrottleService.extractClientIp(request);
        //1.登录前先检查是否已被限流
        loginThrottleService.assertAllowed(normalizedAccount, clientIp);

        try {
            // 2. 账号密码校验
            User user = authService.login(normalizedAccount, req.getPassword());

            // 3. 登录成功后沿用你当前已有的 session / security 逻辑
            //登录成功后轮换 Session ID，防止会话固定攻击
            //攻击者可能先想办法让受害者带着一个已知的 Session ID 访问你的网站。
            //如果受害者登录后，服务器还继续沿用这个旧 Session ID，攻击者就可能利用这个已知 ID 冒充受害者。
            //先确保当前请求已经有 session
            request.getSession(true);
            //登录成功后轮换 Session ID，防止会话固定攻击
            request.changeSessionId();
            //拿到轮换后的当前 session
            HttpSession session = request.getSession(false);
            if (session == null) {
                throw new IllegalStateException("Failed to create a valid session after successful login");
            }
            // 创建当前用户的安全身份对象;包含
            //用户是谁
            //凭证（密码等）
            //权限/角色有哪些
            //当前是否已认证
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    user.getUsername(),
                    null,
                    AuthorityUtils.createAuthorityList("ROLE_USER")
            );
            //创建SecurityContext，把“当前用户已经登录”这件事写进
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            // 用 SecurityContextRepository 保存上下文，而不是自己手工 setAttribute
            securityContextRepository.saveContext(context, request, response);

            //在 SessionRegistry 中登记当前新 session
            String currentSessionId = session.getId();
            userSessionService.registerCurrentSession(user.getUsername(), currentSessionId);

            //把同账号其他旧 session 全部标记为过期，只保留当前新 session
            userSessionService.expireOtherSessions(user.getUsername(), currentSessionId);

            // 4. 登录成功，自动清空账号/IP 的节流状态
            loginThrottleService.recordSuccess(normalizedAccount, clientIp);

            return ApiResponse.success("Login succeeded", user.getId());
        } catch (BadCredentialsException e) {
            // 5. 只有账号密码错误时，才累计失败次数
            loginThrottleService.recordFailure(normalizedAccount, clientIp);
            throw e;
        }

    }

    @PostMapping("/code")
    public ApiResponse<Void> sendCode(
            //这个 email 参数是从请求参数里取的，不是从 JSON body 里取
            @RequestParam
            @NotBlank(message = "Email cannot be empty")
            @Email(message = "Invalid email format")
            String email
    ){
        authService.sendCode(email);
        return ApiResponse.success("Verification code sent");
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody @Valid RegisterRequest req) {
        authService.register(
                req.getEmail(),
                req.getUsername(),
                req.getPassword(),
                req.getCode()
                );
        return ApiResponse.success("Register succeeded");
    }

    @PostMapping("/password/change")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestBody @Valid ChangePwdRequest req,
            Principal principal
            ){
        if(principal == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401,"Not logged in"));
        }
        authService.changePassword(
                principal.getName(),
                req.getOldPassword(),
                req.getNewPassword()
        );
        return  ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@RequestBody @Valid ResetPwdRequest req){
        authService.resetPassword(
                req.getEmail(),
                req.getCode(),
                req.getNewPassword());
            return  ApiResponse.success("Password reset successfully");

    }
}
