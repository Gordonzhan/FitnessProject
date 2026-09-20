package com.fitness.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 生产模式启动前校验数据库、OSS 与匿名比较任务的最低安全配置。 */
@Component
public class ProductionInfrastructureConfigurationValidator {
    @Value("${fitness.production-mode:false}")
    private boolean productionMode;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${fitness.oss.endpoint:}")
    private String ossEndpoint;

    @Value("${fitness.oss.access-key-id:}")
    private String ossAccessKeyId;

    @Value("${fitness.oss.access-key-secret:}")
    private String ossAccessKeySecret;

    @Value("${fitness.oss.bucket-name:}")
    private String ossBucketName;

    @Value("${fitness.comparison.include-synthetic:false}")
    private boolean includeSyntheticComparison;

    @Value("${fitness.comparison.real-snapshot-enabled:false}")
    private boolean realComparisonSnapshotEnabled;

    @Value("${fitness.comparison.hash-secret:}")
    private String comparisonHashSecret;

    @PostConstruct
    void validate() {
        if (!productionMode) return;

        List<String> problems = new ArrayList<>();
        if (!validMysqlUrl(datasourceUrl)) {
            problems.add("数据库地址必须是非本机的 MySQL JDBC 地址");
        }
        if (!StringUtils.hasText(datasourceUsername)) problems.add("必须配置数据库用户名");
        if (!StringUtils.hasText(datasourcePassword)) problems.add("必须配置数据库密码");
        if (!validHttpsEndpoint(ossEndpoint)) problems.add("OSS Endpoint 必须是有效的 HTTPS 地址");
        if (!StringUtils.hasText(ossAccessKeyId)) problems.add("必须配置 OSS AccessKey ID");
        if (!StringUtils.hasText(ossAccessKeySecret)) problems.add("必须配置 OSS AccessKey Secret");
        if (!StringUtils.hasText(ossBucketName)) problems.add("必须配置 OSS Bucket 名称");
        if (includeSyntheticComparison) problems.add("生产环境必须关闭 R7 合成样本");
        if (realComparisonSnapshotEnabled && byteLength(comparisonHashSecret) < 32) {
            problems.add("启用 R7 真实匿名快照时必须配置至少 32 字节的独立 HMAC 密钥");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("生产基础设施配置不安全：" + String.join("；", problems));
        }
    }

    private boolean validMysqlUrl(String value) {
        if (!StringUtils.hasText(value) || !value.startsWith("jdbc:mysql://")) return false;
        String authorityAndPath = value.substring("jdbc:mysql://".length());
        int separator = authorityAndPath.indexOf('/');
        String authority = separator < 0 ? authorityAndPath : authorityAndPath.substring(0, separator);
        String host = authority;
        if (host.startsWith("[")) {
            int closing = host.indexOf(']');
            host = closing < 0 ? host : host.substring(1, closing);
        } else {
            int colon = host.indexOf(':');
            if (colon >= 0) host = host.substring(0, colon);
        }
        return StringUtils.hasText(host)
                && !"localhost".equalsIgnoreCase(host)
                && !"127.0.0.1".equals(host)
                && !"::1".equals(host);
    }

    private boolean validHttpsEndpoint(String value) {
        if (!StringUtils.hasText(value)) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && StringUtils.hasText(uri.getHost())
                    && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private int byteLength(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
