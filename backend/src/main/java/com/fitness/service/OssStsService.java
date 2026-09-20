package com.fitness.service;

import com.aliyun.sts20150401.models.AssumeRoleRequest;
import com.aliyun.sts20150401.models.AssumeRoleResponse;
import com.aliyun.sts20150401.models.AssumeRoleResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 只在服务端获取并消费 STS Secret；响应客户端时绝不返回 Secret。 */
@Service
public class OssStsService {
    private final String accessKeyId;
    private final String accessKeySecret;
    private final String roleArn;
    private final String region;
    private final String bucketName;
    private final ObjectMapper objectMapper;

    public OssStsService(
            @Value("${fitness.oss.access-key-id:}") String accessKeyId,
            @Value("${fitness.oss.access-key-secret:}") String accessKeySecret,
            @Value("${fitness.oss.sts-role-arn:}") String roleArn,
            @Value("${fitness.oss.region:cn-beijing}") String region,
            @Value("${fitness.oss.bucket-name:}") String bucketName,
            ObjectMapper objectMapper) {
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        this.roleArn = roleArn;
        this.region = region;
        this.bucketName = bucketName;
        this.objectMapper = objectMapper;
    }

    public TemporaryCredentials assumeForObject(String objectKey, String ticketId) {
        try {
            validateConfiguration();
            Config config = new Config().setAccessKeyId(accessKeyId).setAccessKeySecret(accessKeySecret);
            config.endpoint = "sts." + region + ".aliyuncs.com";
            com.aliyun.sts20150401.Client client = new com.aliyun.sts20150401.Client(config);
            AssumeRoleRequest request = new AssumeRoleRequest()
                    .setRoleArn(roleArn)
                    .setRoleSessionName("fitness-upload-" + ticketId.replace("-", ""))
                    // STS 最短有效期为 900 秒；真正上传窗口由更短的 Post Policy 控制。
                    .setDurationSeconds(900L)
                    .setPolicy(sessionPolicy(objectKey));
            AssumeRoleResponse response = client.assumeRoleWithOptions(request, new RuntimeOptions());
            AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials credentials = response.body.credentials;
            if (credentials == null || isBlank(credentials.accessKeyId)
                    || isBlank(credentials.accessKeySecret) || isBlank(credentials.securityToken)) {
                throw new IllegalStateException("STS未返回完整临时凭证");
            }
            return new TemporaryCredentials(
                    credentials.accessKeyId, credentials.accessKeySecret, credentials.securityToken);
        } catch (Exception error) {
            throw new AuthorizationException(error);
        }
    }

    String sessionPolicy(String objectKey) throws JsonProcessingException {
        Map<String, Object> statement = Map.of(
                "Effect", "Allow",
                "Action", List.of("oss:PutObject", "oss:GetObjectMeta", "oss:GetObject", "oss:DeleteObject"),
                "Resource", List.of("acs:oss:*:*:" + bucketName + "/" + objectKey));
        return objectMapper.writeValueAsString(Map.of("Version", "1", "Statement", List.of(statement)));
    }

    private void validateConfiguration() {
        if (isBlank(accessKeyId) || isBlank(accessKeySecret) || isBlank(roleArn)
                || isBlank(region) || isBlank(bucketName)) {
            throw new IllegalStateException("OSS直传配置缺失，请检查STS角色、Region和服务端凭证");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record TemporaryCredentials(String accessKeyId, String accessKeySecret, String securityToken) {
    }

    public static class AuthorizationException extends RuntimeException {
        public AuthorizationException(Throwable cause) {
            super("OSS上传授权暂不可用", cause);
        }
    }
}
