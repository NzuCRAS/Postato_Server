package com.potato.auth;

import com.potato.user.User;
import com.potato.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 统一认证过滤器:从 Authorization: Bearer <token> 解析身份。
 * - token 以 "mcp_" 开头 → 当作 API Key,查 users.apiKeys
 * - 否则当作 JWT 解析
 * 解析成功则把 User 放入 SecurityContext 作为 principal。
 */
@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthTokenFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7).trim();
            boolean viaApiKey = token.startsWith("mcp_");
            Optional<User> user = resolveUser(token);
            user.ifPresent(u -> {
                List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>(
                        u.getFunctions().stream()
                                .map(f -> new SimpleGrantedAuthority("ROLE_" + f))
                                .toList());
                authorities.add(new SimpleGrantedAuthority(viaApiKey ? "CHANNEL_APIKEY" : "CHANNEL_JWT"));
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(u, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }
        filterChain.doFilter(request, response);
    }

    private Optional<User> resolveUser(String token) {
        if (token.startsWith("mcp_")) {
            return userRepository.findByApiKeysKey(token);
        }
        try {
            String userId = jwtService.parseUserId(token);
            return userRepository.findById(userId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
