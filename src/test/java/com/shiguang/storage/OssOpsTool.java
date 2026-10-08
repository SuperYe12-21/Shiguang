package com.shiguang.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.SetBucketCORSRequest.CORSRule;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.LifecycleRule;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.SetBucketCORSRequest;
import com.aliyun.oss.model.SetBucketLifecycleRequest;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.messages.Item;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 一次性运维工具（只在本机开发时用，默认不参与 mvn test）。
 *
 * 用法：把 OSS 参数填进 .devtools/oss.env 后执行
 *   mvn test -Dtest=OssOpsTool -Doss.tool=run
 *
 * 三个动作彼此独立，可以单独跑：
 *   -Dtest="OssOpsTool#verifyCredentials"   只读验证 AK / 桶名 / 权限
 *   -Dtest="OssOpsTool#setupBucket"         配桶：公共读 + CORS + source/ 生命周期
 *   -Dtest="OssOpsTool#syncFromMinio"       把本地 MinIO 的对象按原 key 复制到 OSS
 */
@EnabledIfSystemProperty(named = "oss.tool", matches = "run")
class OssOpsTool {

    private static final String ENV_FILE = ".devtools/oss.env";

    /** 本地 MinIO（与 application-dev.yml 保持一致） */
    private static final String MINIO_ENDPOINT = "http://127.0.0.1:9000";
    private static final String MINIO_AK = "minioadmin";
    private static final String MINIO_SK = "minioadmin";
    private static final String MINIO_BUCKET = "shiguang-media";

    /**
     * 允许直传的来源。部署时用 -Doss.corsOrigins=https://你的域名 覆盖（多个用逗号分隔）。
     * 注意别写成 *：预签名地址本身就是凭证，来源放开等于谁拿到链接都能传。
     */
    private static final List<String> ALLOWED_ORIGINS = List.of(
            System.getProperty("oss.corsOrigins",
                    // 后面几个是本机联调用的：手机通过局域网地址访问时，Origin 是 http://192.168.x.x:5173，
                    // 不放行的话手机只能看不能发布（上传走 PUT，命中这条规则）
                    "http://localhost:5173,http://localhost:4173,http://123.57.252.14,http://192.168.*:5173").split(","));

    // ---------------------------------------------------------------- 0. 列出账号下的桶

    /** 用来确认「AK 属于哪个账号、这个账号到底有哪些桶、桶在哪个地域」 */
    @Test
    void listBuckets() {
        OssConfig cfg = OssConfig.load();
        OSS oss = cfg.newClient();
        List<com.aliyun.oss.model.Bucket> buckets = oss.listBuckets();
        System.out.println("[OSS] 该 AK 名下共 " + buckets.size() + " 个桶:");
        buckets.forEach(b -> System.out.printf("  - %s  地域=%s  创建时间=%s%n",
                b.getName(), b.getLocation(), b.getCreationDate()));
        boolean configuredExists = buckets.stream().anyMatch(b -> b.getName().equals(cfg.bucket()));
        System.out.println("[OSS] oss.env 里写的桶 " + cfg.bucket() + " " + (configuredExists ? "存在 ✓" : "不在列表里 ✗"));
        oss.shutdown();
    }

    // ---------------------------------------------------------------- 1. 只读验证

    /**
     * 用「挨个地域试 getBucketAcl」的方式反推桶到底在哪个地域：
     * 地域不对时 OSS 会报 "does not belong to you"，地域对了才会走到权限校验（我们的策略允许 GetBucketAcl）。
     */
    @Test
    void findBucketRegion() {
        OssConfig cfg = OssConfig.load();
        List<String> endpoints = List.of(
                "oss-cn-beijing.aliyuncs.com",
                "oss-cn-zhangjiakou.aliyuncs.com",
                "oss-cn-hangzhou.aliyuncs.com",
                "oss-cn-shanghai.aliyuncs.com",
                "oss-cn-qingdao.aliyuncs.com",
                "oss-cn-shenzhen.aliyuncs.com",
                "oss-cn-guangzhou.aliyuncs.com",
                "oss-cn-heyuan.aliyuncs.com",
                "oss-cn-chengdu.aliyuncs.com",
                "oss-cn-wulanchabu.aliyuncs.com",
                "oss-cn-nanjing.aliyuncs.com",
                "oss-cn-fuzhou.aliyuncs.com",
                "oss-cn-wuhan-lr.aliyuncs.com",
                "oss-cn-hongkong.aliyuncs.com");
        String hit = null;
        for (String endpoint : endpoints) {
            OSS oss = new OSSClientBuilder().build(endpoint, cfg.accessKeyId(), cfg.accessKeySecret());
            try {
                var acl = oss.getBucketAcl(cfg.bucket());
                System.out.println("  ✓ " + endpoint + "  ->  ACL=" + acl.getCannedACL());
                hit = endpoint;
            } catch (com.aliyun.oss.OSSException e) {
                System.out.println("  ✗ " + endpoint + "  ->  " + e.getErrorCode() + ": "
                        + String.valueOf(e.getErrorMessage()).split("\n")[0]);
            } catch (Exception e) {
                System.out.println("  ? " + endpoint + "  ->  " + e.getMessage());
            } finally {
                try {
                    oss.shutdown();
                } catch (Exception ignored) {
                    // 忽略
                }
            }
        }
        if (hit == null) {
            System.out.println("[OSS] 所有地域都报同一种错 -> 桶和 AK 很可能不属于同一个阿里云账号");
        } else {
            System.out.println("[OSS] 桶所在地域的 endpoint 是: " + hit + "（把它填进 oss.env 的 OSS_ENDPOINT）");
        }
    }

    @Test
    void verifyCredentials() {
        OssConfig cfg = OssConfig.load();
        OSS oss = cfg.newClient();
        System.out.println("[OSS] bucket=" + cfg.bucket() + " endpoint=" + cfg.endpoint());
        // 不用 doesBucketExist：它走的是另一条内部 API，在这个 RAM 策略下会误报 "does not belong to you"
        var acl = oss.getBucketAcl(cfg.bucket());
        System.out.println("[OSS] ✓ 桶可访问，ACL = " + acl.getCannedACL());
        try {
            System.out.println("[OSS] 地域 = " + oss.getBucketLocation(cfg.bucket()));
        } catch (Exception e) {
            System.out.println("[OSS] 地域查询未授权（不影响使用）: " + e.getMessage());
        }
        try {
            System.out.println("[OSS] 已有 CORS 规则数 = " + oss.getBucketCORSRules(cfg.bucket()).size());
        } catch (Exception e) {
            System.out.println("[OSS] 暂无 CORS 规则 (" + String.valueOf(e.getMessage()).split("\n")[0] + ")");
        }
        try {
            System.out.println("[OSS] 已有生命周期规则数 = " + oss.getBucketLifecycle(cfg.bucket()).size());
        } catch (Exception e) {
            System.out.println("[OSS] 暂无生命周期规则 (" + String.valueOf(e.getMessage()).split("\n")[0] + ")");
        }
        oss.shutdown();
    }


    // ---------------------------------------------------------------- 2. 配桶

    @Test
    void setupBucket() {
        OssConfig cfg = OssConfig.load();
        OSS oss = cfg.newClient();

        // 媒体本来就是给所有人看的；source/ 上传源文件由下面的生命周期规则兜底清理
        oss.setBucketAcl(cfg.bucket(), CannedAccessControlList.PublicRead);
        System.out.println("[OSS] ACL 已设为 " + oss.getBucketAcl(cfg.bucket()));

        // 读取规则单独一条、来源放开：<video crossorigin="anonymous">（抓帧做续播快照用）属于 CORS 请求，
        // 手机用局域网地址访问时 Origin 是 http://192.168.x.x:5173，不在上传白名单里，拿不到 ACAO 就整片加载失败。
        // 媒体本来就是公共读的，GET 放开来源不泄露任何东西（谁都能直接下这个 URL）。
        CORSRule mediaRule = new CORSRule();
        mediaRule.setAllowedOrigins(List.of("*"));
        mediaRule.setAllowedMethods(List.of("GET", "HEAD"));
        mediaRule.setAllowedHeaders(List.of("*"));
        mediaRule.setExposeHeaders(List.of("ETag"));
        mediaRule.setMaxAgeSeconds(600);

        // 上传仍然只放行白名单来源：预签名地址本身就是凭证，来源放开等于谁拿到链接都能往桶里写
        CORSRule uploadRule = new CORSRule();
        uploadRule.setAllowedOrigins(ALLOWED_ORIGINS);
        uploadRule.setAllowedMethods(List.of("PUT", "POST"));
        // 必须放开请求头：浏览器直传会先发 OPTIONS 预检，带 Access-Control-Request-Headers: content-type，
        // 这里不写 AllowedHeaders 的话 OSS 会直接 403（CORSResponse），PUT 根本发不出去
        uploadRule.setAllowedHeaders(List.of("*"));
        uploadRule.setExposeHeaders(List.of("ETag"));
        uploadRule.setMaxAgeSeconds(600);

        SetBucketCORSRequest cors = new SetBucketCORSRequest(cfg.bucket());
        cors.addCorsRule(mediaRule);
        cors.addCorsRule(uploadRule);
        oss.setBucketCORS(cors);
        System.out.println("[OSS] CORS 已配置: 读取(GET/HEAD)=* 上传(PUT/POST)=" + ALLOWED_ORIGINS);

        // 注意：setBucketLifecycle 会整体覆盖，所以先读出来再合并
        SetBucketLifecycleRequest lifecycle = new SetBucketLifecycleRequest(cfg.bucket());
        List<LifecycleRule> rules = new ArrayList<>();
        try {
            rules.addAll(oss.getBucketLifecycle(cfg.bucket()));
        } catch (Exception ignored) {
            // 桶里还没有任何规则
        }
        boolean sourceExpiryExists = rules.stream()
                .anyMatch(r -> "source/".equals(r.getPrefix()) && Integer.valueOf(1).equals(r.getExpirationDays()));
        if (!sourceExpiryExists) {
            rules.add(new LifecycleRule("shiguang-clean-source", "source/", LifecycleRule.RuleStatus.Enabled, 1));
        }
        rules.forEach(lifecycle::AddLifecycleRule);
        oss.setBucketLifecycle(lifecycle);
        System.out.println("[OSS] 生命周期规则: " + rules.stream()
                .map(r -> r.getPrefix() + " -> " + r.getExpirationDays() + " 天")
                .toList());
        oss.shutdown();
    }

    // ---------------------------------------------------------------- 3. 迁移对象

    @Test
    void syncFromMinio() throws Exception {
        OssConfig cfg = OssConfig.load();
        OSS oss = cfg.newClient();
        MinioClient minio = MinioClient.builder()
                .endpoint(MINIO_ENDPOINT)
                .credentials(MINIO_AK, MINIO_SK)
                .build();

        List<String> keys = new ArrayList<>();
        Map<String, Long> sizes = new HashMap<>();
        for (Result<Item> result : minio.listObjects(ListObjectsArgs.builder()
                .bucket(MINIO_BUCKET)
                .recursive(true)
                .build())) {
            Item item = result.get();
            if (item.isDir()) {
                continue;
            }
            keys.add(item.objectName());
            sizes.put(item.objectName(), item.size());
        }
        long totalBytes = sizes.values().stream().mapToLong(Long::longValue).sum();
        System.out.printf("[迁移] MinIO 共 %d 个对象，%.1f MB%n", keys.size(), totalBytes / 1024.0 / 1024.0);

        int copied = 0;
        int skipped = 0;
        for (String key : keys) {
            StatObjectResponse stat = minio.statObject(StatObjectArgs.builder()
                    .bucket(MINIO_BUCKET)
                    .object(key)
                    .build());
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(stat.size());
            meta.setContentType(stat.contentType() == null ? "application/octet-stream" : stat.contentType());
            try (var in = minio.getObject(GetObjectArgs.builder()
                    .bucket(MINIO_BUCKET)
                    .object(key)
                    .build())) {
                oss.putObject(cfg.bucket(), key, in, meta);
            }
            copied++;
            System.out.printf("  + %s (%.2f MB)%n", key, stat.size() / 1024.0 / 1024.0);
        }
        System.out.printf("[迁移] 完成：复制 %d，跳过 %d%n", copied, skipped);
        oss.shutdown();
    }

    // ---------------------------------------------------------------- 配置读取

    /** 打印桶上生效的 CORS 规则：-Dtest="OssOpsTool#dumpCors" -Doss.tool=run */
    @Test
    void dumpCors() {
        OssConfig cfg = OssConfig.load();
        OSS oss = cfg.newClient();
        List<CORSRule> rules = oss.getBucketCORSRules(cfg.bucket());
        System.out.println("[OSS] CORS 规则数 = " + rules.size());
        int i = 1;
        for (CORSRule r : rules) {
            System.out.println("  #" + i++);
            System.out.println("    Origin : " + r.getAllowedOrigins());
            System.out.println("    Methods: " + r.getAllowedMethods());
            System.out.println("    Headers: " + r.getAllowedHeaders());
            System.out.println("    Expose : " + r.getExposeHeaders());
            System.out.println("    MaxAge : " + r.getMaxAgeSeconds());
        }
        oss.shutdown();
    }

    /** 列出某个前缀下的对象：-Dtest="OssOpsTool#listPrefix" -Doss.tool=run -Doss.prefix=videos/2026-10-08/ */
    @Test
    void listPrefix() {
        OssConfig cfg = OssConfig.load();
        OSS oss = cfg.newClient();
        String prefix = System.getProperty("oss.prefix", "");
        String nextMarker = null;
        long count = 0;
        long bytes = 0;
        do {
            com.aliyun.oss.model.ListObjectsRequest request = new com.aliyun.oss.model.ListObjectsRequest(cfg.bucket());
            request.setPrefix(prefix);
            request.setMaxKeys(1000);
            request.setMarker(nextMarker);
            com.aliyun.oss.model.ObjectListing listing = oss.listObjects(request);
            for (com.aliyun.oss.model.OSSObjectSummary s : listing.getObjectSummaries()) {
                System.out.printf("  %s (%.2f MB)%n", s.getKey(), s.getSize() / 1048576.0);
                count++;
                bytes += s.getSize();
            }
            nextMarker = listing.isTruncated() ? listing.getNextMarker() : null;
        } while (nextMarker != null);
        System.out.printf("[OSS] prefix=%s count=%d total=%.2f MB%n", prefix, count, bytes / 1048576.0);
        oss.shutdown();
    }

    /** 删对象（清理测试残留）：-Dtest="OssOpsTool#deleteKeys" -Doss.tool=run -Doss.keys=a.mp4,b.jpg */
    @Test
    void deleteKeys() {
        OssConfig cfg = OssConfig.load();
        OSS oss = cfg.newClient();
        String keys = System.getProperty("oss.keys", "");
        assertThat(keys).as("用 -Doss.keys=key1,key2 指定要删除的对象").isNotBlank();
        for (String key : keys.split(",")) {
            String trimmed = key.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            oss.deleteObject(cfg.bucket(), trimmed);
            System.out.println("[OSS] 已删除 " + trimmed);
        }
        oss.shutdown();
    }

    /** 核对迁移结果：两边对象数、总大小是否一致，并列出缺失的 key（输出全用 ASCII，避免控制台编码问题） */
    @Test
    void verifySync() throws Exception {
        OssConfig cfg = OssConfig.load();
        OSS oss = cfg.newClient();
        MinioClient minio = MinioClient.builder()
                .endpoint(MINIO_ENDPOINT)
                .credentials(MINIO_AK, MINIO_SK)
                .build();

        Map<String, Long> local = new HashMap<>();
        for (Result<Item> result : minio.listObjects(ListObjectsArgs.builder()
                .bucket(MINIO_BUCKET).recursive(true).build())) {
            Item item = result.get();
            if (!item.isDir()) {
                local.put(item.objectName(), item.size());
            }
        }
        Map<String, Long> remote = new HashMap<>();
        String nextMarker = null;
        do {
            com.aliyun.oss.model.ListObjectsRequest request = new com.aliyun.oss.model.ListObjectsRequest(cfg.bucket());
            request.setMaxKeys(1000);
            request.setMarker(nextMarker);
            com.aliyun.oss.model.ObjectListing listing = oss.listObjects(request);
            listing.getObjectSummaries().forEach(s -> remote.put(s.getKey(), s.getSize()));
            nextMarker = listing.isTruncated() ? listing.getNextMarker() : null;
        } while (nextMarker != null);

        List<String> missing = local.keySet().stream().filter(k -> !remote.containsKey(k)).sorted().toList();
        List<String> sizeMismatch = local.entrySet().stream()
                .filter(e -> remote.containsKey(e.getKey()) && !remote.get(e.getKey()).equals(e.getValue()))
                .map(e -> e.getKey() + " local=" + e.getValue() + " oss=" + remote.get(e.getKey()))
                .sorted().toList();

        System.out.println("MINIO_COUNT=" + local.size() + " MINIO_MB=" + local.values().stream().mapToLong(Long::longValue).sum() / 1048576.0);
        System.out.println("OSS_COUNT=" + remote.size() + " OSS_MB=" + remote.values().stream().mapToLong(Long::longValue).sum() / 1048576.0);
        System.out.println("MISSING=" + missing.size() + (missing.isEmpty() ? "" : " -> " + missing));
        System.out.println("SIZE_MISMATCH=" + sizeMismatch.size() + (sizeMismatch.isEmpty() ? "" : " -> " + sizeMismatch));
        assertThat(missing).as("有对象没迁过去").isEmpty();
        assertThat(sizeMismatch).as("有对象大小不一致").isEmpty();
        oss.shutdown();
    }

    private record OssConfig(String endpoint, String bucket, String accessKeyId, String accessKeySecret) {

        static OssConfig load() {
            Path path = Path.of(ENV_FILE);
            assertThat(Files.exists(path))
                    .as("缺少 " + ENV_FILE + "（从 oss.env.example 复制并填写）")
                    .isTrue();
            Map<String, String> env = new HashMap<>();
            try {
                for (String line : Files.readAllLines(path)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int idx = trimmed.indexOf('=');
                    if (idx <= 0) {
                        continue;
                    }
                    env.put(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim());
                }
            } catch (Exception e) {
                throw new IllegalStateException("读取 " + ENV_FILE + " 失败", e);
            }
            String endpoint = env.getOrDefault("OSS_ENDPOINT", "");
            String bucket = env.getOrDefault("OSS_BUCKET", "");
            String ak = env.getOrDefault("OSS_ACCESS_KEY_ID", "");
            String sk = env.getOrDefault("OSS_ACCESS_KEY_SECRET", "");
            assertThat(endpoint).as("OSS_ENDPOINT 未填").isNotBlank();
            assertThat(bucket).as("OSS_BUCKET 未填").isNotBlank();
            assertThat(ak).as("OSS_ACCESS_KEY_ID 未填").isNotBlank();
            assertThat(sk).as("OSS_ACCESS_KEY_SECRET 未填").isNotBlank();
            return new OssConfig(endpoint, bucket, ak, sk);
        }

        OSS newClient() {
            return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        }
    }
}
