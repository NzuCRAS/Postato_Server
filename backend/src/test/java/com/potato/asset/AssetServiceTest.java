package com.potato.asset;

import com.potato.storage.StorageService;
import com.potato.storage.StoredObject;
import com.potato.wiki.WikiPage;
import com.potato.wiki.WikiPageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetServiceTest {

    @Mock
    StorageService storageService;
    @Mock
    WikiPageRepository wikiPageRepository;

    AssetService service;

    @BeforeEach
    void setUp() {
        service = new AssetService(storageService, wikiPageRepository);
    }

    private WikiPage pageWithAsset(String id, String title, String path, String objectKey, String name, String mime) {
        WikiPage p = new WikiPage();
        p.setId(id);
        p.setTitle(title);
        p.setPath(path);
        if (objectKey != null) {
            WikiPage.Asset a = new WikiPage.Asset();
            a.setObjectKey(objectKey);
            a.setName(name);
            a.setContentType(mime);
            p.getAssets().add(a);
        }
        return p;
    }

    @Test
    void cross_references_pages_and_enriches_metadata() throws Exception {
        WikiPage p1 = pageWithAsset("id1", "页一", "/a", "wiki/id1/x-demo.html", "demo.html", "text/html");
        WikiPage p2 = pageWithAsset("id2", "页二", "/b", "wiki/id1/x-demo.html", "demo.html", "text/html");
        when(wikiPageRepository.findAllByOrderByPathAsc()).thenReturn(List.of(p1, p2));
        when(storageService.listObjects()).thenReturn(List.of(
                new StoredObject("wiki/id1/x-demo.html", 100L, null, "http://x/wiki/id1/x-demo.html")));

        List<AssetView> views = service.listAssets();

        assertThat(views).hasSize(1);
        AssetView v = views.get(0);
        assertThat(v.orphan()).isFalse();
        assertThat(v.name()).isEqualTo("demo.html");
        assertThat(v.contentType()).isEqualTo("text/html");
        assertThat(v.referencingPages()).extracting(AssetView.PageRef::id).containsExactly("id1", "id2");
    }

    @Test
    void flags_orphan_when_no_referencing_page() throws Exception {
        when(wikiPageRepository.findAllByOrderByPathAsc()).thenReturn(List.of());
        when(storageService.listObjects()).thenReturn(List.of(
                new StoredObject("wiki/old/orphan.png", 50L, null, "http://x/wiki/old/orphan.png")));

        List<AssetView> views = service.listAssets();

        assertThat(views).hasSize(1);
        AssetView v = views.get(0);
        assertThat(v.orphan()).isTrue();
        assertThat(v.referencingPages()).isEmpty();
        assertThat(v.name()).isEqualTo("orphan.png"); // 无引用页时从 key 尾段派生
    }
}
