package com.potato.storage;

import com.potato.wiki.WikiPage;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SetBucketPolicyArgs;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 唯一封装对象存储(MinIO)的地方。上传走后端代理,开发期桶公开读。 */
@Service
public class StorageService {

    private final MinioClient client;
    private final String bucket;
    private final String publicEndpoint;

    public StorageService(
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.public-endpoint}") String publicEndpoint,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey,
            @Value("${app.minio.bucket}") String bucket) {
        this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
        this.bucket = bucket;
        this.publicEndpoint = publicEndpoint;
    }

    /** 启动确保桶存在并设匿名只读(开发期 demo/图片可直接被浏览器加载)。 */
    @PostConstruct
    void ensureBucket() throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
                + "\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
        client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
    }

    /** 上传一个文件,返回可写进 WikiPage 的 Asset 元数据。 */
    public WikiPage.Asset upload(String pageId, MultipartFile file) throws Exception {
        String safeName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String key = "wiki/" + pageId + "/" + UUID.randomUUID() + "-" + safeName;
        client.putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                .build());
        WikiPage.Asset a = new WikiPage.Asset();
        a.setName(safeName);
        a.setObjectKey(key);
        a.setUrl(publicEndpoint + "/" + bucket + "/" + key);
        a.setContentType(file.getContentType());
        a.setSize(file.getSize());
        a.setUploadedAt(Instant.now());
        return a;
    }

    public void delete(String objectKey) throws Exception {
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    }

    /** 列出桶内所有对象(递归);供资产库可视化与交叉引用扫描用。 */
    public List<StoredObject> listObjects() throws Exception {
        List<StoredObject> out = new ArrayList<>();
        Iterable<Result<Item>> results = client.listObjects(
                ListObjectsArgs.builder().bucket(bucket).recursive(true).build());
        for (Result<Item> r : results) {
            Item item = r.get();
            if (item.isDir()) continue;
            String key = item.objectName();
            Instant lastModified = item.lastModified() == null ? null : item.lastModified().toInstant();
            out.add(new StoredObject(key, item.size(), lastModified, publicEndpoint + "/" + bucket + "/" + key));
        }
        return out;
    }
}
