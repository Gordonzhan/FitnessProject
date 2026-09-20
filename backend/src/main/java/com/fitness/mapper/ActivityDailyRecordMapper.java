package com.fitness.mapper;

import com.fitness.entity.ActivityDailyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ActivityDailyRecordMapper {
    int upsert(ActivityDailyRecord record);

    List<ActivityDailyRecord> findByDateRange(@Param("userId") Long userId,
                                               @Param("startDate") LocalDate startDate,
                                               @Param("endDate") LocalDate endDate);

    int deleteByUserIdAndSource(@Param("userId") Long userId, @Param("source") String source);
}
