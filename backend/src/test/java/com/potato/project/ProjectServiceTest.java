package com.potato.project;

import com.potato.common.DocLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    ProjectRepository repo;

    ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(repo);
    }

    @Test
    void create_then_add_repo_and_doclink() {
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        Project p = service.create("Proj", "desc", "u1");
        p.setId("p1");
        assertThat(p.getName()).isEqualTo("Proj");
        when(repo.findById("p1")).thenReturn(Optional.of(p));

        Project.Repo r = new Project.Repo();
        r.setUrl("https://github.com/x/y");
        Project withRepo = service.addRepo("p1", r);
        assertThat(withRepo.getRepos()).hasSize(1);
        assertThat(withRepo.getRepos().get(0).getId()).startsWith("repo_");

        DocLink d = new DocLink();
        d.setType("design");
        d.setTitle("T");
        d.setPath("/x/y");
        Project withDoc = service.addDocLink("p1", d);
        assertThat(withDoc.getDocLinks()).hasSize(1);
        assertThat(withDoc.getDocLinks().get(0).getPath()).isEqualTo("/x/y");
    }

    @Test
    void create_requires_name() {
        assertThatThrownBy(() -> service.create("", null, "u"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
