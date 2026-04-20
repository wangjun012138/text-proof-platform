package com.wangjun.text_proof_platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangjun.text_proof_platform.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.session.HttpSessionEventPublisher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.security.web.context.SecurityContextRepository;
@Configuration
public class SecurityConfig {
    //SessionRegistry:系统里专门记录“某一个用户对应哪些 session”的一个登记簿。
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    //而是服务端先根据 sessionId 找到 HttpSession，
    // 再从 HttpSession 中取出 SecurityContext，再从里面拿到当前用户信息。

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    //向 Spring 容器注册一条 安全过滤链。
    //所有 HTTP 请求在进入 Controller 之前，都会先经过这条链。
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            SessionRegistry sessionRegistry,
            ObjectMapper objectMapper,
            SecurityContextRepository securityContextRepository
    ) throws Exception {
        http
                // 生产环境建议强制所有请求必须是 HTTPS
                // 如果请求不是 HTTPS，Spring Security 会重定向到 HTTPS
                .requiresChannel(channel -> channel
                        .anyRequest().requiresSecure()
                )

                // 安全响应头：告诉浏览器以后优先使用 HTTPS
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31536000)
                        )
                )
                //CookieCsrfTokenRepository表示把 CSRF Token 存到 Cookie 中。
                //withHttpOnlyFalse()表示这个 Cookie 不设置 HttpOnly=true，也就是：前端 JS 可以读到这个 Cookie。
                .csrf(csrf -> csrf.
                        csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        // token 分享查看 / 下载允许匿名访问
                        .requestMatchers(HttpMethod.GET, "/api/share/token/**").permitAll()

                        .requestMatchers(
                                "/api/auth/csrf",
                                "/api/auth/code",
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/password/reset"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                //401和403先进入Spring Security过滤器链，安全通过后才能进入AuthController，所以这两个错误放在这
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJsonResponse(
                                        response,
                                        objectMapper,
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        ApiResponse.error(401, "Not logged in")
                                ))

                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJsonResponse(
                                        response,
                                        objectMapper,
                                        HttpServletResponse.SC_FORBIDDEN,
                                        ApiResponse.error(403, "Access denied")
                                ))
                )
                //控制登录会话并发。
                .sessionManagement(session -> session
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                        .expiredSessionStrategy(event ->
                                writeJsonResponse(
                                        event.getResponse(),
                                        objectMapper,
                                        HttpServletResponse.SC_UNAUTHORIZED,
                                        ApiResponse.error(401,
                                                "Account has been logged in on another device, current session has expired.")
                                ))
                        .sessionRegistry(sessionRegistry)
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }

    @Bean
    //这个 Bean 会一直存在于 Spring 容器中，当 session 发生变动时，
    // HttpSessionEventPublisher 会把这个变动通知给 Spring Security；
    // 然后 Spring Security 再结合你配置的会话管理规则去更新状态或作出处理。
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
    //统一写 JSON 响应的工具方法。
    private void writeJsonResponse(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            ApiResponse<Void> body
    ) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
