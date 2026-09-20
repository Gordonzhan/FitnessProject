package com.fitness.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OssStsServiceTest {
    @Test void sessionPolicyLimitsActionsToOneExactObject() throws Exception {
        ObjectMapper json = new ObjectMapper();
        OssStsService service = new OssStsService(
                "id", "secret", "acs:ram::1:role/upload", "cn-beijing", "bucket", json);
        String key = "fitness-diary/images/7/977b707b-4444-41e3-aef9-9c58d0fa7b28.jpeg";

        Map<String, Object> policy = json.readValue(service.sessionPolicy(key), new TypeReference<>() {});
        List<?> statements = (List<?>) policy.get("Statement");
        Map<?, ?> statement = (Map<?, ?>) statements.get(0);
        assertEquals(List.of("acs:oss:*:*:bucket/" + key), statement.get("Resource"));
        assertEquals(List.of("oss:PutObject", "oss:GetObjectMeta", "oss:GetObject", "oss:DeleteObject"),
                statement.get("Action"));
        assertFalse(service.sessionPolicy(key).contains("bucket/*"));
    }
}
