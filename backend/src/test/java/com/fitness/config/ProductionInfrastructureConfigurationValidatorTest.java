package com.fitness.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionInfrastructureConfigurationValidatorTest {
    @Test
    void localModeDoesNotRequireCloudInfrastructure() {
        ProductionInfrastructureConfigurationValidator validator = validator(false);
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void productionModeRejectsLocalOrMissingInfrastructureAndSyntheticSamples() {
        ProductionInfrastructureConfigurationValidator validator = validator(true);
        set(validator, "datasourceUrl", "jdbc:mysql://localhost:3306/fitness_diary");
        set(validator, "includeSyntheticComparison", true);
        set(validator, "realComparisonSnapshotEnabled", true);
        set(validator, "comparisonHashSecret", "short");

        IllegalStateException error = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(error.getMessage().contains("非本机的 MySQL JDBC 地址"));
        assertTrue(error.getMessage().contains("数据库用户名"));
        assertTrue(error.getMessage().contains("数据库密码"));
        assertTrue(error.getMessage().contains("HTTPS"));
        assertTrue(error.getMessage().contains("AccessKey ID"));
        assertTrue(error.getMessage().contains("AccessKey Secret"));
        assertTrue(error.getMessage().contains("Bucket"));
        assertTrue(error.getMessage().contains("关闭 R7 合成样本"));
        assertTrue(error.getMessage().contains("独立 HMAC 密钥"));
        assertFalse(error.getMessage().contains("short"));
    }

    @Test
    void productionModeAcceptsExistingProxyUploadInfrastructure() {
        ProductionInfrastructureConfigurationValidator validator = validator(true);
        set(validator, "datasourceUrl", "jdbc:mysql://mysql.internal:3306/fitness_diary?useSSL=true");
        set(validator, "datasourceUsername", "fitness_app");
        set(validator, "datasourcePassword", "database-secret");
        set(validator, "ossEndpoint", "https://oss-cn-beijing.aliyuncs.com");
        set(validator, "ossAccessKeyId", "access-key-id");
        set(validator, "ossAccessKeySecret", "access-key-secret");
        set(validator, "ossBucketName", "fitness-images");
        set(validator, "includeSyntheticComparison", false);
        set(validator, "realComparisonSnapshotEnabled", false);

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void enabledRealSnapshotRequiresStrongIndependentSecret() {
        ProductionInfrastructureConfigurationValidator validator = validator(true);
        set(validator, "datasourceUrl", "jdbc:mysql://mysql.internal:3306/fitness_diary");
        set(validator, "datasourceUsername", "fitness_app");
        set(validator, "datasourcePassword", "database-secret");
        set(validator, "ossEndpoint", "https://oss-cn-beijing.aliyuncs.com");
        set(validator, "ossAccessKeyId", "access-key-id");
        set(validator, "ossAccessKeySecret", "access-key-secret");
        set(validator, "ossBucketName", "fitness-images");
        set(validator, "realComparisonSnapshotEnabled", true);
        set(validator, "comparisonHashSecret", "0123456789abcdef0123456789abcdef");

        assertDoesNotThrow(validator::validate);
    }

    private ProductionInfrastructureConfigurationValidator validator(boolean productionMode) {
        ProductionInfrastructureConfigurationValidator validator =
                new ProductionInfrastructureConfigurationValidator();
        set(validator, "productionMode", productionMode);
        set(validator, "datasourceUrl", "");
        set(validator, "datasourceUsername", "");
        set(validator, "datasourcePassword", "");
        set(validator, "ossEndpoint", "");
        set(validator, "ossAccessKeyId", "");
        set(validator, "ossAccessKeySecret", "");
        set(validator, "ossBucketName", "");
        set(validator, "includeSyntheticComparison", false);
        set(validator, "realComparisonSnapshotEnabled", false);
        set(validator, "comparisonHashSecret", "");
        return validator;
    }

    private void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }
}
