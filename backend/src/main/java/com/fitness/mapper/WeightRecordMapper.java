package com.fitness.mapper;

import com.fitness.entity.WeightRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WeightRecordMapper {
    void insert(WeightRecord record);

    /** 每个业务日期只返回 id 最大（即最后写入）的一条，仍保留原始同日多次记录。 */
    List<WeightRecord> findLatestByDateRange(@Param("userId") Long userId,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);
}
