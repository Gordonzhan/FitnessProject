package com.fitness.service;

import com.fitness.dto.DailyNutritionSummary;
import com.fitness.dto.DailyWorkoutSummary;
import com.fitness.entity.User;
import com.fitness.entity.WeightRecord;
import com.fitness.mapper.DailyIntakeMapper;
import com.fitness.mapper.WeightRecordMapper;
import com.fitness.mapper.WorkoutMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalysisServiceTest {
    private final DailyIntakeMapper intakes = mock(DailyIntakeMapper.class);
    private final WorkoutMapper workouts = mock(WorkoutMapper.class);
    private final WeightRecordMapper weights = mock(WeightRecordMapper.class);
    private final UserService users = mock(UserService.class);
    private AnalysisService service;

    @BeforeEach
    void setUp() {
        service = new AnalysisService(intakes, workouts, weights, users);
    }

    @Test
    void aggregatesSnapshotsAndCountsAllWorkoutStatesButOnlyActualCompletedCalories() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 3);
        User user = user();
        when(users.findById(1L)).thenReturn(user);
        when(users.calculateDailyCalories(user)).thenReturn(1800d);
        when(intakes.aggregateByDateRange(1L, start, end)).thenReturn(List.of(
                nutrition(start, "1800", "120", "200", "60"),
                nutrition(start.plusDays(1), "1800", "125", "205", "62"),
                nutrition(end, "1800", "130", "210", "64")));
        when(workouts.aggregateByDateRange(1L, start, end)).thenReturn(List.of(
                workout(start, "300", 1, 0, 0),
                workout(start.plusDays(1), "0", 0, 1, 0),
                workout(end, "0", 0, 0, 1)));
        when(weights.findLatestByDateRange(1L, start, end)).thenReturn(List.of(
                weight(start, "70.0"), weight(end, "69.6")));

        var result = service.analyze(1L, start, end);

        assertEquals(new BigDecimal("300.00"), result.dailyPoints().get(0).actualExpenditure());
        assertEquals(new BigDecimal("0.00"), result.dailyPoints().get(1).actualExpenditure());
        assertEquals(new BigDecimal("1700.00"), result.averages().energyDifference());
        assertEquals(new BigDecimal("-100.00"), result.averages().targetDifference());
        assertEquals(1, result.trainingCompletion().completed());
        assertEquals(1, result.trainingCompletion().planned());
        assertEquals(1, result.trainingCompletion().cancelled());
        assertEquals(new BigDecimal("33.3"), result.trainingCompletion().rate());
        assertEquals("COMPLETE", result.dataState());
        assertEquals(new BigDecimal("-0.40"), result.goalAssessment().weightChange());
    }

    @Test
    void missingNutritionDaysAreNotTreatedAsZeroIntakeInStageAverage() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 7);
        User user = user();
        when(users.findById(1L)).thenReturn(user);
        when(users.calculateDailyCalories(user)).thenReturn(1800d);
        when(intakes.aggregateByDateRange(1L, start, end))
                .thenReturn(List.of(nutrition(start, "1800", "100", "200", "50")));
        when(workouts.aggregateByDateRange(1L, start, end)).thenReturn(List.of());
        when(weights.findLatestByDateRange(1L, start, end)).thenReturn(List.of());

        var result = service.analyze(1L, start, end);

        assertEquals(new BigDecimal("1800.00"), result.averages().intakeCalories());
        assertEquals(new BigDecimal("0.00"), result.averages().targetDifference());
        assertEquals("PARTIAL", result.dataState());
        assertEquals("DATA_INSUFFICIENT", result.goalAssessment().status());

        when(intakes.aggregateByDateRange(1L, start, end)).thenReturn(List.of());
        var empty = service.analyze(1L, start, end);
        assertEquals("NO_DATA", empty.dataState());
        assertEquals("DATA_INSUFFICIENT", empty.goalAssessment().status());
    }

    @Test
    void rejectsReversedFutureAndOverNinetyDayRanges() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        assertThrows(IllegalArgumentException.class, () -> service.analyze(1L, today, today.minusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.analyze(1L, today, today.plusDays(1)));
        assertThrows(IllegalArgumentException.class, () -> service.analyze(1L, today.minusDays(90), today));
    }

    @Test
    void sameDayManualWeightIsInsertedAndSynchronizesCurrentProfile() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        service.recordWeight(1L, today, new BigDecimal("68.55"));
        verify(weights).insert(any(WeightRecord.class));
        verify(users).updateCurrentWeight(1L, new BigDecimal("68.55"));
    }

    private User user() {
        User user = new User();
        user.setWeight(new BigDecimal("70"));
        user.setGoal("lose_weight");
        return user;
    }

    private DailyNutritionSummary nutrition(LocalDate date, String calories, String protein, String carb, String fat) {
        DailyNutritionSummary row = new DailyNutritionSummary();
        row.setDate(date);
        row.setCalories(new BigDecimal(calories));
        row.setProtein(new BigDecimal(protein));
        row.setCarb(new BigDecimal(carb));
        row.setFat(new BigDecimal(fat));
        row.setRecordCount(2);
        return row;
    }

    private DailyWorkoutSummary workout(LocalDate date, String actual, int completed, int planned, int cancelled) {
        DailyWorkoutSummary row = new DailyWorkoutSummary();
        row.setDate(date);
        row.setActualCalories(new BigDecimal(actual));
        row.setCompletedCount(completed);
        row.setPlannedCount(planned);
        row.setCancelledCount(cancelled);
        return row;
    }

    private WeightRecord weight(LocalDate date, String value) {
        WeightRecord record = new WeightRecord();
        record.setRecordDate(date);
        record.setWeight(new BigDecimal(value));
        return record;
    }
}
