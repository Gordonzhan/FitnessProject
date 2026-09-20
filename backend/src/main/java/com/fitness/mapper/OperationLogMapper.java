package com.fitness.mapper;

import com.fitness.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

@Mapper
public interface OperationLogMapper {
    void insert(OperationLog operationLog);

    int deleteBefore(@Param("cutoff") Date cutoff);
}
