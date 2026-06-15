package com.potato.config;

import com.potato.permission.PermissionRule;
import com.potato.permission.PermissionRuleRepository;
import com.potato.permission.dict.ActionDef;
import com.potato.permission.dict.ActionDefRepository;
import com.potato.permission.dict.FunctionDef;
import com.potato.permission.dict.FunctionDefRepository;
import com.potato.permission.dict.PermissionDef;
import com.potato.permission.dict.PermissionDefRepository;
import com.potato.permission.dict.ResourceDef;
import com.potato.permission.dict.ResourceDefRepository;
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
import java.util.function.Supplier;

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
    private final FunctionDefRepository functionDefRepository;
    private final ResourceDefRepository resourceDefRepository;
    private final ActionDefRepository actionDefRepository;
    private final String adminPassword;

    public DataSeeder(UserRepository userRepository,
                      PermissionRuleRepository permissionRuleRepository,
                      ProjectRepository projectRepository,
                      PasswordEncoder passwordEncoder,
                      FunctionDefRepository functionDefRepository,
                      ResourceDefRepository resourceDefRepository,
                      ActionDefRepository actionDefRepository,
                      @Value("${app.seed.admin-password:admin123}") String adminPassword) {
        this.userRepository = userRepository;
        this.permissionRuleRepository = permissionRuleRepository;
        this.projectRepository = projectRepository;
        this.passwordEncoder = passwordEncoder;
        this.functionDefRepository = functionDefRepository;
        this.resourceDefRepository = resourceDefRepository;
        this.actionDefRepository = actionDefRepository;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        seedAdmin();
        seedDefaultProject();
        seedPermissionDicts();
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

    /** 幂等注册三类字典:职能 / 资源 / 全局动作。规则引用的取值必须出自这里。 */
    private void seedPermissionDicts() {
        upsertDef(functionDefRepository, FunctionDef::new, "admin", "管理员");
        upsertDef(functionDefRepository, FunctionDef::new, "product", "产品");
        upsertDef(functionDefRepository, FunctionDef::new, "development", "开发");
        upsertDef(functionDefRepository, FunctionDef::new, "testing", "测试");

        upsertDef(resourceDefRepository, ResourceDef::new, "requirement", "需求");
        upsertDef(resourceDefRepository, ResourceDef::new, "wiki", "知识库");
        upsertDef(resourceDefRepository, ResourceDef::new, "dev_plan", "进度树");
        upsertDef(resourceDefRepository, ResourceDef::new, "project", "项目");
        upsertDef(resourceDefRepository, ResourceDef::new, "arch", "结构树");
        upsertDef(resourceDefRepository, ResourceDef::new, "user", "用户");
        upsertDef(resourceDefRepository, ResourceDef::new, "permission", "权限");

        // 动作池需覆盖现有规则用到的全部 action(否则规则引用校验会让存量规则改不动)
        upsertDef(actionDefRepository, ActionDef::new, "view", "查看");
        upsertDef(actionDefRepository, ActionDef::new, "read", "读取");
        upsertDef(actionDefRepository, ActionDef::new, "create", "创建");
        upsertDef(actionDefRepository, ActionDef::new, "edit", "编辑");
        upsertDef(actionDefRepository, ActionDef::new, "update", "更新");
        upsertDef(actionDefRepository, ActionDef::new, "delete", "删除");
        upsertDef(actionDefRepository, ActionDef::new, "comment", "评论");
        upsertDef(actionDefRepository, ActionDef::new, "edit_structured", "编辑结构化");
        upsertDef(actionDefRepository, ActionDef::new, "update_status", "更新状态");
        upsertDef(actionDefRepository, ActionDef::new, "edit_management", "管理编辑");
        upsertDef(actionDefRepository, ActionDef::new, "sync", "同步");
        upsertDef(actionDefRepository, ActionDef::new, "manage", "管理");
    }

    /** key 不存在才插入(幂等)。 */
    private <T extends PermissionDef> void upsertDef(PermissionDefRepository<T> repo, Supplier<T> factory, String key, String label) {
        if (!repo.existsByKey(key)) {
            T d = factory.get();
            d.setKey(key);
            d.setLabel(label);
            repo.save(d);
        }
    }

    /** 幂等补齐权限规则:逐条按 (resource,action) 缺失才插入,不覆盖已存在/被在线编辑过的规则。 */
    private void seedPermissionRules() {
        int before = (int) permissionRuleRepository.count();
        upsertRule("requirement", "view", "development", "testing", "product");
        upsertRule("requirement", "create", "product");
        upsertRule("requirement", "edit_structured", "product");
        upsertRule("requirement", "update_status", "product");
        upsertRule("wiki", "read", "development", "testing", "product");
        upsertRule("wiki", "edit", "development", "product");
        upsertRule("dev_plan", "create", "development");
        upsertRule("dev_plan", "update", "development");
        upsertRule("dev_plan", "comment", "development", "testing", "product");
        upsertRule("project", "view", "development", "testing", "product");
        upsertRule("project", "create", "product");
        upsertRule("project", "edit", "product");
        upsertRule("arch", "edit_management", "development", "product");
        upsertRule("arch", "sync", "development");
        // 用户管理(仅 admin;admin 本就豁免,规则表达意图并为将来非 admin 留口)
        upsertRule("user", "view", "admin");
        upsertRule("user", "create", "admin");
        upsertRule("user", "update", "admin");
        upsertRule("user", "delete", "admin");
        // 权限规则管理(仅 admin)
        upsertRule("permission", "view", "admin");
        upsertRule("permission", "manage", "admin");
        int added = (int) permissionRuleRepository.count() - before;
        if (added > 0) {
            log.info("==> Seeded {} new permission rules (idempotent top-up).", added);
        }
    }

    /** (resource,action) 不存在才插入。 */
    private void upsertRule(String resource, String action, String... functions) {
        if (permissionRuleRepository.findByResourceAndAction(resource, action).isEmpty()) {
            permissionRuleRepository.save(rule(resource, action, functions));
        }
    }

    private PermissionRule rule(String resource, String action, String... functions) {
        PermissionRule r = new PermissionRule();
        r.setResource(resource);
        r.setAction(action);
        r.setRequiredFunctions(List.of(functions));
        return r;
    }
}
