package com.potato.asset;

import com.potato.storage.StorageService;
import com.potato.storage.StoredObject;
import com.potato.wiki.WikiPage;
import com.potato.wiki.WikiPageRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 资产库:列桶对象,交叉引用 wiki 页 assets[] 算出每个对象被哪些页引用、是否孤儿。 */
@Service
public class AssetService {

    private final StorageService storageService;
    private final WikiPageRepository wikiPageRepository;

    public AssetService(StorageService storageService, WikiPageRepository wikiPageRepository) {
        this.storageService = storageService;
        this.wikiPageRepository = wikiPageRepository;
    }

    public List<AssetView> listAssets() throws Exception {
        // 先扫所有页的 assets[],按 objectKey 归集引用页与一份资产元数据(名/MIME)
        Map<String, List<AssetView.PageRef>> refsByKey = new LinkedHashMap<>();
        Map<String, WikiPage.Asset> metaByKey = new HashMap<>();
        for (WikiPage p : wikiPageRepository.findAllByOrderByPathAsc()) {
            if (p.getAssets() == null) continue;
            for (WikiPage.Asset a : p.getAssets()) {
                if (a.getObjectKey() == null) continue;
                refsByKey.computeIfAbsent(a.getObjectKey(), k -> new ArrayList<>())
                        .add(new AssetView.PageRef(p.getId(), p.getTitle(), p.getPath()));
                metaByKey.putIfAbsent(a.getObjectKey(), a);
            }
        }
        // 再以桶里的对象为准(单一事实来源),富化元数据并标孤儿
        List<AssetView> out = new ArrayList<>();
        for (StoredObject obj : storageService.listObjects()) {
            List<AssetView.PageRef> referencing = refsByKey.getOrDefault(obj.objectKey(), List.of());
            WikiPage.Asset meta = metaByKey.get(obj.objectKey());
            String name = meta != null ? meta.getName() : deriveName(obj.objectKey());
            String contentType = meta != null ? meta.getContentType() : null;
            out.add(new AssetView(obj.objectKey(), name, obj.url(), contentType, obj.size(),
                    obj.lastModified(), referencing, referencing.isEmpty()));
        }
        return out;
    }

    /** 无引用页时从 objectKey 尾段取个显示名。 */
    private String deriveName(String key) {
        int i = key.lastIndexOf('/');
        return i < 0 ? key : key.substring(i + 1);
    }
}
