package com.fitness.mapper;

import com.fitness.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface UserMapper {
    User findByOpenid(@Param("openid") String openid);
    void insert(User user);
    void update(User user);
    User findById(@Param("id") Long id);
    int updateWeight(@Param("id") Long id, @Param("weight") BigDecimal weight);
}
