package com.fitness.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PopulationComparisonSqlContractTest {
    @Test
    void mapperAggregatesBeforeReturningAndSyntheticDataIsExplicitlyGated() throws IOException {
        String mapper = resource("mapper/ComparisonSampleMapper.xml");
        assertTrue(mapper.contains("GROUP BY participant_key, goal, source"));
        assertTrue(mapper.contains("#{includeSynthetic}"));
        assertTrue(mapper.contains("HAVING SUM"));
        assertFalse(mapper.contains("openid"));
        assertFalse(mapper.contains("users"));
    }

    @Test
    void realSnapshotAggregatesBeforeHashingAndNeverCopiesOpenid() throws IOException {
        String mapper = resource("mapper/ComparisonSnapshotMapper.xml");
        assertTrue(mapper.contains("GROUP BY w.user_id, w.date, u.goal"));
        assertTrue(mapper.contains("HAVING COUNT(*)"));
        assertTrue(mapper.contains("source = 'ANONYMIZED'"));
        assertTrue(mapper.contains("masked_display_name"));
        assertTrue(mapper.contains("u.display_name"));
        assertFalse(mapper.contains("openid"));
        assertFalse(mapper.contains("exercises"));
    }

    private String resource(String name) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(name)) {
            if (stream == null) throw new IOException("missing test resource: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
