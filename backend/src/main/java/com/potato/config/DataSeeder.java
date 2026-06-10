package com.potato.config;

import com.potato.permission.PermissionRule;
import com.potato.permission.PermissionRuleRepository;
import com.potato.user.User;
import com.potato.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 启动时 seed:
 * 1. 默认管理员(若无 admin)
 * 2. 权限规则(若 permission_rules 为空)
 * 生产环境请通过 SEED_ADMIN_PASSWORD 覆盖默认密码。
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PermissionRuleRepository permissionRuleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public DataSeeder(UserRepository userRepository,
                      PermissionRuleRepository permissionRuleRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.seed.admin-password:admin123}") String adminPassword) {
        this.userRepository = userRepository;
        this.permissionRuleRepository = permissionRuleRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        seedAdmin();
        seedPermissionRules();
    }

    private void seedAdmin() {
        if (userRepository.findByUsername("admin").isPresent()) {
            return;
        }
        User admin = new User();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setFunctions(List.of("admin", "development"));
        admin.setCreatedAt(Instant.now());
        admin.setUpdatedAt(Instant.now());
        userRepository.save(admin);
        log.info("==> Seeded default admin (username=admin, password={}). CHANGE IT IN PRODUCTION!", adminPassword);
    }

    private void seedPermissionRules() {
        if (permissionRuleRepository.count() > 0) {
            return;
        }
        List<PermissionRule> rules = List.of(
                rule("requirement", "view", "development", "testing", "product"),
                rule("requirement", "create", "product"),
                rule("requirement", "edit_structured", "product"),
                rule("requirement", "update_status", "product"),
                rule("wiki", "read", "development", "testing", "product"),
                rule("wiki", "edit", "product"),
                rule("dev_plan", "create", "development"),
                rule("dev_plan", "update", "development"),
                rule("dev_plan", "comment", "development", "testing", "product")
        );
        permissionRuleRepository.saveAll(rules);
        log.info("==> Seeded {} permission rules.", rules.size());
    }

    private PermissionRule rule(String resource, String action, String... functions) {
        PermissionRule r = new PermissionRule();
        r.setResource(resource);
        r.setAction(action);
        r.setRequiredFunctions(List.of(functions));
        return r;
    }
}
