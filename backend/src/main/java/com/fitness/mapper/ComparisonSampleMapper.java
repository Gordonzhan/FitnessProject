package com.fitness.mapper;

import com.fitness.dto.ComparisonParticipantStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ComparisonSampleMapper {
    List<ComparisonParticipantStats> aggregateByRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("includeSynthetic") boolean includeSynthetic);
}
