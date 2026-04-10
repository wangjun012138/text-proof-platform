package com.wangjun.text_proof_platform.modules.user.service;

import lombok.Data;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

@Service
public class UserSessionService {

    //SessionRegistry:系统里专门记录“哪个用户对应哪些 session”的一个登记簿。
    private final SessionRegistry sessionRegistry;

    public UserSessionService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void registerCurrentSession(String username,String sessionId) {
        sessionRegistry.registerNewSession(sessionId, username);
    }
    //让某个用户当前登记的所有 session 立即过期。
    public void expireSessions(String username) {
        for(SessionInformation sessionInformation : sessionRegistry.getAllSessions(username,false)){
            sessionInformation.expireNow();
        }
    }
    // 让某个用户除当前 session 之外的其它 session 全部过期
    public void expireOtherSessions(String username, String currentSessionId) {
        for (SessionInformation sessionInformation : sessionRegistry.getAllSessions(username, false)) {
            if (!sessionInformation.getSessionId().equals(currentSessionId)) {
                sessionInformation.expireNow();
            }
        }
    }
}
