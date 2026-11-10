package com.fitness.mapper;

import com.fitness.entity.UserTrainingPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserTrainingPreferenceMapper {
    UserTrainingPreference findByUserId(@Param("userId") Long userId);
    void upsert(UserTrainingPreference preference);
}
