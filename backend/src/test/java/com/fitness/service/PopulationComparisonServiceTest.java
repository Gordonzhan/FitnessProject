package com.fitness.service;

import com.fitness.dto.ComparisonParticipantStats;
import com.fitness.entity.User;
import com.fitness.mapper.ComparisonSampleMapper;
import com.fitness.mapper.WorkoutMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PopulationComparisonServiceTest {
    private final ComparisonSampleMapper samples = mock(ComparisonSampleMapper.class);
    private final WorkoutMapper workouts = mock(WorkoutMapper.class);
    private final UserService users = mock(UserService.class);
    private PopulationComparisonService service;

    @BeforeEach
    void setup() {
        service = new PopulationComparisonService(samples, workouts, users);
        ReflectionTestUtils.setField(service, "businessZone", "Asia/Shanghai");
        ReflectionTestUtils.setField(service, "minimumSampleSize", 10);
        ReflectionTestUtils.setField(service, "includeSynthetic", true);
        User user = new User();
        user.setId(7L);
        user.setGoal("maintain");
        when(users.findById(7L)).thenReturn(user);
        when(workouts.aggregateComparisonStats(eq(7L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(stats(null, "maintain", null, 5, 5, 2, 1));
    }

    @Test
    void sameGoalCohortProducesStableAnonymousPercentiles() {
        List<ComparisonParticipantStats> population = new ArrayList<>();
        for (int value = 1; value <= 10; value++) {
            population.add(stats("hidden-" + value, "maintain", "ANONYMIZED", value, value, 2, 1));
        }
        when(samples.aggregateByRange(any(), any(), eq(true))).thenReturn(population);

        var result = service.compareTraining(7L);

        assertEquals("READY", result.status());
        assertEquals("SAME_GOAL", result.cohortCode());
        assertEquals(10, result.sampleSize());
        assertFalse(result.demoData());
        assertEquals(50, result.metrics().get(0).percentile());
        assertNotNull(result.leaderboard());
        assertEquals(6, result.leaderboard().currentRank());
        assertEquals(11, result.leaderboard().totalParticipants());
        assertEquals(10, result.leaderboard().entries().size());
        assertTrue(result.leaderboard().entries().stream()
                .allMatch(item -> "G****n".equals(item.displayName())));
        assertEquals("9–12 次", result.leaderboard().entries().get(0).valueBand());
        assertFalse(result.toString().contains("hidden-"));
    }

    @Test
    void insufficientGoalGroupFallsBackToAllActiveAndLabelsSyntheticDemo() {
        List<ComparisonParticipantStats> population = new ArrayList<>();
        for (int value = 1; value <= 15; value++) {
            String goal = value <= 5 ? "maintain" : "gain_muscle";
            population.add(stats("synthetic-" + value, goal, "SYNTHETIC", value, value, 1, 0));
        }
        when(samples.aggregateByRange(any(), any(), eq(true))).thenReturn(population);

        var result = service.compareTraining(7L);

        assertEquals("READY", result.status());
        assertEquals("ALL_ACTIVE", result.cohortCode());
        assertEquals(15, result.sampleSize());
        assertTrue(result.demoData());
        assertTrue(result.notices().stream().anyMatch(item -> item.contains("扩展")));
        assertTrue(result.notices().stream().anyMatch(item -> item.contains("SYNTHETIC")));
    }

    @Test
    void populationBelowPrivacyThresholdNeverReturnsPercentile() {
        when(samples.aggregateByRange(any(), any(), eq(true))).thenReturn(List.of(
                stats("one", "maintain", "SYNTHETIC", 3, 3, 0, 0)));

        var result = service.compareTraining(7L);

        assertEquals("POPULATION_INSUFFICIENT", result.status());
        assertNull(result.metrics().get(0).percentile());
        assertNull(result.leaderboard());
    }

    @Test
    void currentUserWithoutRecordsIsNotAssignedAMisleadingRank() {
        when(workouts.aggregateComparisonStats(eq(7L), any(), any()))
                .thenReturn(stats(null, null, null, 0, 0, 0, 0));
        List<ComparisonParticipantStats> population = new ArrayList<>();
        for (int value = 1; value <= 15; value++) {
            population.add(stats("sample-" + value, "gain_muscle", "ANONYMIZED", value, value, 0, 0));
        }
        when(samples.aggregateByRange(any(), any(), eq(true))).thenReturn(population);

        var result = service.compareTraining(7L);

        assertEquals("CURRENT_DATA_INSUFFICIENT", result.status());
        assertNull(result.metrics().get(1).percentile());
        assertNull(result.leaderboard());
    }

    @Test
    void leaderboardUsesCoarseBandsInsteadOfExactValues() {
        assertEquals("0 次", service.sessionBand(0));
        assertEquals("1–4 次", service.sessionBand(4));
        assertEquals("5–8 次", service.sessionBand(8));
        assertEquals("9–12 次", service.sessionBand(12));
        assertEquals("13–16 次", service.sessionBand(16));
        assertEquals("17–20 次", service.sessionBand(20));
        assertEquals("21 次以上", service.sessionBand(21));
    }

    private ComparisonParticipantStats stats(String key, String goal, String source,
                                             int completed, int activeDays, int planned, int cancelled) {
        ComparisonParticipantStats item = new ComparisonParticipantStats();
        item.setParticipantKey(key);
        item.setMaskedDisplayName(key == null ? null : "G****n");
        item.setGoal(goal);
        item.setSource(source);
        item.setCompletedCount(completed);
        item.setActiveDays(activeDays);
        item.setPlannedCount(planned);
        item.setCancelledCount(cancelled);
        return item;
    }
}
