package com.wangjun.text_proof_platform.modules.user.service;

import com.wangjun.text_proof_platform.common.TooManyLoginAttemptsException;
import com.wangjun.text_proof_platform.modules.user.entity.LoginThrottleScope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
    @Service
    public class LoginThrottleService {

        //同一个账号，在一个失败窗口内，最多允许失败 5 次
        private static final int ACCOUNT_FAILURE_LIMIT = 5;
        //同一个 IP，在一个失败窗口内，最多允许失败 20 次
        private static final int IP_FAILURE_LIMIT = 20;
        //失败计数不是永久累计，而是在 15 分钟这个时间窗里统计
        private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
        //账号触发限流后，锁 15 分钟
        private static final Duration ACCOUNT_LOCK_DURATION = Duration.ofMinutes(15);
        //IP 触发限流后，也锁 15 分钟
        private static final Duration IP_LOCK_DURATION = Duration.ofMinutes(15);

        // key 形如 ACCOUNT:alice 或 IP:127.0.0.1,key -> FailureState
        //ConcurrentHashMap:普通 HashMap 线程不安全，而这里是多线程 Web 服务，所以至少底层容器要并发安全
        private final Map<String, FailureState> failureStates = new ConcurrentHashMap<>();
        //登录前先检查:只要账号维度或 IP 维度任意一个被限流，都不允许继续登录
        public void assertAllowed(String accountKey, String clientIp) {
            ensureAllowed(
                    LoginThrottleScope.ACCOUNT,
                    normalizeAccountKey(accountKey),
                    "Too many attempts. Please try again later"
            );
            ensureAllowed(
                    LoginThrottleScope.IP,
                    normalizeIpKey(clientIp),
                    "Too many attempts. Please try again later"
            );
        }
        //登录失败后记一次
        //synchronized:在整个 recordFailure 上加同步，避免并发更新错乱。
        public synchronized void recordFailure(String accountKey, String clientIp) {
            registerFailure(
                    LoginThrottleScope.ACCOUNT,
                    normalizeAccountKey(accountKey),
                    ACCOUNT_FAILURE_LIMIT,
                    ACCOUNT_LOCK_DURATION
            );
            registerFailure(
                    LoginThrottleScope.IP,
                    normalizeIpKey(clientIp),
                    IP_FAILURE_LIMIT,
                    IP_LOCK_DURATION
            );
        }
        //登录成功后清限流状态
        public synchronized void recordSuccess(String accountKey, String clientIp) {
            clearThrottle(LoginThrottleScope.ACCOUNT, normalizeAccountKey(accountKey));
            clearThrottle(LoginThrottleScope.IP, normalizeIpKey(clientIp));
        }
        //从请求里提客户端 IP
        public String extractClientIp(HttpServletRequest request) {

            String remoteAddr = normalizeIpKey(request.getRemoteAddr());

            // 只有当请求确实来自可信代理时，才信任 X-Forwarded-For / X-Real-IP
            if (isTrustedProxy(remoteAddr)) {
                String forwardedFor = extractForwardedFor(request.getHeader("X-Forwarded-For"));
                if (StringUtils.hasText(forwardedFor)) {
                    return forwardedFor;
                }
                String realIp = normalizeForwardedIp(request.getHeader("X-Real-IP"));
                if (StringUtils.hasText(realIp)) {
                    return realIp;
                }
            }

            return remoteAddr;
        }
        //真正决定要不要抛异常
        private void ensureAllowed(LoginThrottleScope scope, String scopeKey, String message) {
            //如果 key 为空，就不检查:比如账号/IP 解析异常时，不至于直接报空指针。
            if (!StringUtils.hasText(scopeKey)) {
                return;
            }
            //查这个 scopeKey 当前还要等多久
            OptionalInt retryAfter = getRetryAfterSeconds(scope, scopeKey);
            if (retryAfter.isPresent()) {
                throw new TooManyLoginAttemptsException(message, retryAfter.getAsInt());
            }
        }
        //算还要等多久
        private synchronized OptionalInt getRetryAfterSeconds(LoginThrottleScope scope, String scopeKey) {
            //取出当前状态,如果没有状态，说明这个账号/IP 从未失败过，或者状态已经清掉，直接返回空。
            FailureState state = failureStates.get(compositeKey(scope, scopeKey));
            if (state == null) {
                return OptionalInt.empty();
            }

            Instant now = Instant.now();

            // 仍在锁定期，返回剩余秒数
            if (state.lockedUntil != null && state.lockedUntil.isAfter(now)) {
                long retryAfterSeconds = Duration.between(now, state.lockedUntil).getSeconds();
                return OptionalInt.of((int) Math.max(retryAfterSeconds, 1));
            }

            // 如果锁定期结束了，或者失败窗口结束了，就把状态删掉(懒清理)
            if ((state.lockedUntil != null && !state.lockedUntil.isAfter(now))
                    || !state.windowEndsAt.isAfter(now)) {
                failureStates.remove(compositeKey(scope, scopeKey));
            }

            return OptionalInt.empty();
        }
        //记录失败次数
        private void registerFailure(LoginThrottleScope scope,//ACCOUNT或者IP
                                     String scopeKey,//ACCOUNT或者IP的具体值
                                     int failureLimit,//失败多少次后锁定
                                     Duration lockDuration//触发锁定，要锁多久
        ) {
            //如果账号键 / IP 键是空字符串、null、全空格，那这次不记失败。
            if (!StringUtils.hasText(scopeKey)) {
                return;
            }
            //获取当前时间、拼完整 key、读取旧状态
            Instant now = Instant.now();
            String key = compositeKey(scope, scopeKey);
            FailureState current = failureStates.get(key);

            // 没有旧状态，或者以前虽然触发过锁定，但锁定时间已经结束了，或者失败统计窗口已经结束

            if (current == null
                    || (current.lockedUntil != null && !current.lockedUntil.isAfter(now))
                    || !current.windowEndsAt.isAfter(now)) {
                //不沿用旧状态了，直接从 1 次失败重新开始记
                Instant lockedUntil = failureLimit <= 1 ? now.plus(lockDuration) : null;
                //窗口截止时间设为：当前时间 + 失败窗口长度
                failureStates.put(key, new FailureState(1, now.plus(FAILURE_WINDOW), lockedUntil));
                return;
            }
            //如果没有重新开窗口，就说明还在旧窗口里继续累计
            int nextCount = current.failureCount + 1;
            //把旧的锁定截止时间拷过来。
            Instant lockedUntil = current.lockedUntil;
            //如果累计失败次数已经达到或超过阈值，就进入锁定期
            if (nextCount >= failureLimit) {
                lockedUntil = now.plus(lockDuration);
            }
            //把新状态放回 map
            failureStates.put(key, new FailureState(nextCount, current.windowEndsAt, lockedUntil));
        }
        //登录成功后调用,把某个账号/IP 的限流状态整个删掉
        private void clearThrottle(LoginThrottleScope scope, String scopeKey) {
            failureStates.remove(compositeKey(scope, scopeKey));
        }
        //拼 map key:ACCOUNT:alice  IP:127.0.0.1
        private String compositeKey(LoginThrottleScope scope, String scopeKey) {
            return scope.name() + ":" + scopeKey;
        }
        //账号标准化:
        private String normalizeAccountKey(String accountKey) {
            if (!StringUtils.hasText(accountKey)) {
                return "";
            }
            return accountKey.trim().toLowerCase();
        }
        //IP 标准化
        private String normalizeIpKey(String clientIp) {
            if (!StringUtils.hasText(clientIp)) {
                return "unknown";
            }
            return clientIp.trim();
        }

        //从请求头 X-Forwarded-For 里，取出第一个有效的客户端 IP
        private String extractForwardedFor(String header) {
            //先判断请求头有没有内容
            if (!StringUtils.hasText(header)) {
                return "";
            }

            //X-Forwarded-For 头通常可能包含多个 IP,
            String[] parts = header.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                //过滤非法 IP
                String ip = normalizeAndValidateIp(parts[i]);
                if (!StringUtils.hasText(ip)) {
                    continue;
                }
                //跳过可信代理，返回第一个非可信 IP
                if (!isTrustedProxy(ip)) {
                    return ip;
                }
            }
            return "";
        }
        //清理并校验 IP
        private String normalizeAndValidateIp(String raw) {
            if (!StringUtils.hasText(raw)) {
                return "";
            }
            //去掉前后空格
            String candidate = raw.trim();
            if ("unknown".equalsIgnoreCase(candidate) || !looksLikeIpLiteral(candidate)) {
                return "";
            }
            //尝试解析为 IP
            try {
                return InetAddress.getByName(candidate).getHostAddress();
            } catch (UnknownHostException ex) {
                return "";
            }
        }
        //只接受 IP 字面量，避免把主机名当成 IP 并触发 DNS 解析
        private boolean looksLikeIpLiteral(String candidate) {
            if (candidate.contains(":")) {
                return candidate.matches("[0-9a-fA-F:.]+");
            }
            return candidate.matches(
                    "((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}"
                            + "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)"
            );
        }
        //简单清理 IP 字符串
        private String normalizeForwardedIp(String rawIp) {
            return normalizeAndValidateIp(rawIp);
        }
        //可信代理列表
        private final List<IpAddressMatcher> trustedProxyMatchers = List.of(
                new IpAddressMatcher("127.0.0.1/32"),
                new IpAddressMatcher("::1/128"),
                new IpAddressMatcher("10.10.0.0/16")
        );

        //判断当前请求直接连接过来的这个 IP，是不是一个“可信代理”地址。
        private boolean isTrustedProxy(String remoteAddr) {
            //空值判断
            if (!StringUtils.hasText(remoteAddr)) {
                return false;
            }

            try {
                //把字符串 IP 转成标准 IP
                InetAddress address = InetAddress.getByName(remoteAddr);
                String normalizedIp = address.getHostAddress();
                //判断是否命中可信代理范围
                return trustedProxyMatchers.stream()
                        .anyMatch(matcher -> matcher.matches(normalizedIp));
            } catch (UnknownHostException ex) {
                return false;
            }
        }
        //map 里存的值
        private static class FailureState {
            //已经失败了的次数
            private final int failureCount;
            //失败窗口什么时候结束
            private final Instant windowEndsAt;
            //如果触发锁定，什么时候结束，默认值为null
            private final Instant lockedUntil;

            private FailureState(int failureCount, Instant windowEndsAt, Instant lockedUntil) {
                this.failureCount = failureCount;
                this.windowEndsAt = windowEndsAt;
                this.lockedUntil = lockedUntil;
            }
        }
    }
