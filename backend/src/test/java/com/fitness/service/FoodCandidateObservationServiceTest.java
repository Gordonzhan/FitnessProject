package com.fitness.service;

import com.fitness.mapper.FoodDatabaseCandidateMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FoodCandidateObservationServiceTest {
    private final FoodDatabaseCandidateMapper mapper = mock(FoodDatabaseCandidateMapper.class);
    private final FoodCandidateObservationService service = new FoodCandidateObservationService(mapper);

    @Test
    void normalizesEquivalentNamesBeforeAtomicObservation() {
        service.observe(List.of(new FoodCandidateObservationService.Observation(" 清蒸鱼（大份） ", "蒸制")));

        verify(mapper).observe("清蒸鱼（大份）", "清蒸鱼大份", "蒸制");
    }
}
