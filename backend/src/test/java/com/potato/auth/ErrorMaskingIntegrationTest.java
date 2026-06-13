package com.potato.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归:被保护端点抛出的真实错误不应被 /error 重派掩盖成 401。
 *
 * 历史 bug(已修):端点抛 ResponseStatusException(如 404)→ Spring ERROR dispatch 到 /error →
 * OncePerRequestFilter 默认跳过 error dispatch → /error 未认证 → anyRequest().authenticated() →
 * authenticationEntryPoint 返回 401,掩盖真实状态。修复:SecurityConfig 放行 /error。
 *
 * 该掩盖只有真实 servlet 容器的 error dispatch 才能复现(MockMvc 不重派),故用 RANDOM_PORT 集成测试。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorMaskingIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Test
    void secured_endpoint_real_404_is_not_masked_as_401() {
        // 登录拿 JWT(DataSeeder 已 seed admin/admin123)
        ResponseEntity<Map> login = rest.postForEntity("/api/v1/auth/login",
                Map.of("username", "admin", "password", "admin123"), Map.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        String token = (String) login.getBody().get("token");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        // GET 一个不存在的需求 → service 抛 404「需求不存在」
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1/requirements/nonexistent-id-xyz",
                HttpMethod.GET, new HttpEntity<>(headers), String.class);

        // 修复后应是真实 404,而非被掩盖的 401
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }
}
