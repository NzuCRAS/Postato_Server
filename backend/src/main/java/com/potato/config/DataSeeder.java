package com.potato.config;

import com.potato.permission.PermissionRule;
import com.potato.permission.PermissionRuleRepository;
import com.potato.project.Project;
import com.potato.project.ProjectRepository;
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
    private final ProjectRepository projectRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public DataSeeder(UserRepository userRepository,
                      PermissionRuleRepository permissionRuleRepository,
                      ProjectRepository projectRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.seed.admin-password:admin123}") String adminPassword) {
        this.userRepository = userRepository;
        this.permissionRuleRepository = permissionRuleRepository;
        this.projectRepository = projectRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        seedAdmin();
        seedDefaultProject();
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

    private void seedDefaultProject() {
        if (projectRepository.findById("default").isPresent()) {
            return;
        }
        Project p = new Project();
        p.setId("default");
        p.setName("Potato 平台");
        p.setDescriptionMd("默认项目(自举):用平台开发平台。");
        Project.Repo repo = new Project.Repo();
        repo.setId("repo_default");
        repo.setName("Postato_Server");
        repo.setUrl("https://github.com/NzuCRAS/Postato_Server");
        repo.setProvider("github");
        repo.setDefaultBranch("main");
        p.getRepos().add(repo);
        Instant now = Instant.now();
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        projectRepository.save(p);
        log.info("==> Seeded default project (id=default).");
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
                rule("wiki", "edit", "development", "product"),
                rule("dev_plan", "create", "development"),
                rule("dev_plan", "update", "development"),
                rule("dev_plan", "comment", "development", "testing", "product"),
                rule("project", "view", "development", "testing", "product"),
                rule("project", "create", "product"),
                rule("project", "edit", "product"),
                rule("arch", "edit_management", "development", "product"),
                rule("arch", "sync", "development")
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
