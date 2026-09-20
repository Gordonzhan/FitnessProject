package com.fitness.mapper;

import com.fitness.dto.ComparisonDailySample;
import com.fitness.dto.ComparisonDailySource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ComparisonSnapshotMapper {
    List<ComparisonDailySource> findEligibleDailyAggregates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("minimumEvents") int minimumEvents);

    int deleteAnonymizedRange(@Param("startDate") LocalDate startDate,
                              @Param("endDate") LocalDate endDate,
                              @Param("datasetVersion") String datasetVersion);

    int upsert(ComparisonDailySample sample);
}
