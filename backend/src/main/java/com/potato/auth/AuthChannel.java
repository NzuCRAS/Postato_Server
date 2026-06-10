package com.potato.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * 按认证渠道判定操作者:API Key 渠道 → "ai";JWT(网页)→ "human"。
 * 渠道 authority(CHANNEL_APIKEY / CHANNEL_JWT)由 AuthTokenFilter 注入。
 * 供 DevPlanController、TechProposalController 等共用。
 */
public final class AuthChannel {

    private AuthChannel() {}

    public static String actorOf(Authentication authentication) {
        if (authentication != null) {
            for (GrantedAuthority a : authentication.getAuthorities()) {
                if ("CHANNEL_APIKEY".equals(a.getAuthority())) return "ai";
            }
        }
        return "human";
    }
}
