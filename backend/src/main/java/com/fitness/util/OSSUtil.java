package com.fitness.util;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Component
public class OSSUtil {
    private final String endpoint;
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String bucketName;
    private final String imageDir;
    private final Semaphore uploadSlots = new Semaphore(4);

    public OSSUtil(
            @Value("${fitness.oss.endpoint}") String endpoint,
            @Value("${fitness.oss.access-key-id:}") String accessKeyId,
            @Value("${fitness.oss.access-key-secret:}") String accessKeySecret,
            @Value("${fitness.oss.bucket-name:}") String bucketName,
            @Value("${fitness.oss.image-dir:fitness-diary/images/}") String imageDir) {
        this.endpoint = endpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.bucketName = bucketName;
        this.imageDir = imageDir.endsWith("/") ? imageDir : imageDir + "/";
    }

    public String uploadImage(MultipartFile file, Long userId) throws Exception {
        validateConfiguration();
        if (userId == null || userId <= 0) throw new IllegalArgumentException("登录用户无效");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要上传的图片");
        if (file.getSize() > 20L * 1024 * 1024) throw new IllegalArgumentException("单张图片不能超过20MB");
        if (!uploadSlots.tryAcquire()) throw new UploadBusyException();
        try {
            String format;
            try (InputStream input = file.getInputStream()) {
                format = detectFormat(input.readNBytes(12));
            }
            String key = imageDir + userId + "/" + UUID.randomUUID() + "." + format;
            OSS client = createClient();
            try (InputStream input = file.getInputStream()) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType("image/" + format);
                metadata.setContentLength(file.getSize());
                client.putObject(bucketName, key, input, metadata);
                return "https://" + bucketHost() + "/" + key;
            } finally {
                client.shutdown();
            }
        } finally {
            uploadSlots.release();
        }
    }

    // 不依赖客户端文件名或 Content-Type，拒绝 HTML/XML/SVG 等非支持的图片内容。
    public static String detectFormat(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255) return "jpeg";
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10})) return "png";
        String header = new String(bytes, StandardCharsets.ISO_8859_1);
        if (header.startsWith("GIF87a") || header.startsWith("GIF89a")) return "gif";
        if (header.startsWith("RIFF") && header.length() >= 12 && header.substring(8, 12).equals("WEBP")) return "webp";
        throw new IllegalArgumentException("仅支持 JPG、PNG、GIF、WebP 图片，请转换格式后上传");
    }

    public void deleteImage(String imageUrl) {
        String key = objectKey(imageUrl);
        deleteKnownObjectKey(key);
    }

    /** 删除服务端签发过的精确图片 key；调用方不得传入客户端自定义 key。 */
    public void deleteObjectKey(String key) {
        validateGeneratedKey(key);
        deleteKnownObjectKey(key);
    }

    private void deleteKnownObjectKey(String key) {
        validateConfiguration();
        OSS client = createClient();
        try {
            // OSS 删除不存在的对象也是成功，重复清理安全。
            client.deleteObject(bucketName, key);
        } finally {
            client.shutdown();
        }
    }

    /** 读取对象元数据和最多前12字节，确认接口不下载完整图片。 */
    public RemoteImage inspectImageObject(String key) {
        validateGeneratedKey(key);
        validateConfiguration();
        OSS client = createClient();
        try {
            ObjectMetadata metadata = client.getObjectMetadata(bucketName, key);
            GetObjectRequest request = new GetObjectRequest(bucketName, key);
            request.setRange(0, 11);
            try (OSSObject object = client.getObject(request);
                 InputStream input = object.getObjectContent()) {
                return new RemoteImage(metadata.getContentLength(), metadata.getContentType(), input.readNBytes(12));
            } catch (Exception error) {
                if (error instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("读取OSS图片文件头失败", error);
            }
        } finally {
            client.shutdown();
        }
    }

    public String imageUrlForKey(String key) {
        validateGeneratedKey(key);
        return "https://" + bucketHost() + "/" + key;
    }

    public String uploadHost() {
        validateConfiguration();
        return "https://" + bucketHost();
    }

    public String bucketName() {
        validateConfiguration();
        return bucketName;
    }

    public String imageKey(Long userId, String format) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("登录用户无效");
        if (format == null || !format.matches("jpeg|png|gif|webp")) {
            throw new IllegalArgumentException("图片格式无效");
        }
        return imageDir + userId + "/" + UUID.randomUUID() + "." + format;
    }

    public boolean belongsToUser(String imageUrl, Long userId) {
        String key = objectKey(imageUrl);
        return userId != null && key.startsWith(imageDir + userId + "/")
                && imageUrl.equals("https://" + bucketHost() + "/" + key);
    }

    public String objectKey(String imageUrl) {
        try {
            URI uri = URI.create(imageUrl);
            String path = uri.getRawPath();
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || !bucketHost().equalsIgnoreCase(uri.getHost())
                    || uri.getPort() != -1 || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || path == null || !path.startsWith("/" + imageDir)) {
                throw new IllegalArgumentException();
            }
            String relative = path.substring(imageDir.length() + 1);
            // 兼容历史 UUID 图片，同时拒绝目录穿越、编码路径及其他 Bucket。
            if (!relative.matches("(?:[1-9][0-9]*/)?[0-9a-fA-F-]{36}\\.(?i:jpg|jpeg|png|gif|webp)")) {
                throw new IllegalArgumentException();
            }
            return path.substring(1);
        } catch (Exception e) {
            throw new IllegalArgumentException("图片地址无效或不属于当前OSS图片目录");
        }
    }

    private void validateGeneratedKey(String key) {
        if (key == null || !key.matches(java.util.regex.Pattern.quote(imageDir)
                + "[1-9][0-9]*/[0-9a-fA-F-]{36}\\.(?i:jpeg|png|gif|webp)")) {
            throw new IllegalArgumentException("OSS图片对象标识无效");
        }
    }

    protected OSS createClient() {
        ClientBuilderConfiguration config = new ClientBuilderConfiguration();
        config.setConnectionTimeout(5000);
        config.setConnectionRequestTimeout(5000);
        config.setSocketTimeout(15000);
        config.setRequestTimeout(30000);
        config.setRequestTimeoutEnabled(true);
        config.setMaxErrorRetry(0);
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret, config);
    }

    private String bucketHost() {
        String host = URI.create(endpoint.contains("://") ? endpoint : "https://" + endpoint).getHost();
        return bucketName + "." + host;
    }

    private void validateConfiguration() {
        if (accessKeyId.isBlank() || accessKeySecret.isBlank() || bucketName.isBlank()) {
            throw new IllegalStateException("OSS配置缺失，请检查服务端OSS环境变量");
        }
    }

    public static class UploadBusyException extends RuntimeException {
        public UploadBusyException() { super("图片上传繁忙，请稍后重试"); }
    }

    public record RemoteImage(long size, String contentType, byte[] header) {
    }
}
