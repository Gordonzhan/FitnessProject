package com.fitness.service;

import com.fitness.entity.User;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface UserService {
    /** 根据微信 openid 查询用户档案；不存在时返回 {@code null}。 */
    User getUserByOpenid(String openid);

    /** 新建用户档案并返回持久化后的数据。 */
    User saveUser(User user);

    /** 更新已有用户档案并刷新用户缓存。 */
    User updateUser(User user);

    /** 更新档案并保留当次体重历史；日期为空时使用业务时区的当天。 */
    User updateUser(User user, LocalDate weightRecordDate);

    /** 仅同步当前体重；供当天手动体重记录使用，避免重复写历史。 */
    User updateCurrentWeight(Long userId, BigDecimal weight);

    /** 根据数据库主键查询用户，优先使用短时本地缓存。 */
    User findById(Long id);

    /** 根据身体数据、活动系数和健身目标计算每日建议热量（千卡）。 */
    double calculateDailyCalories(User user);
}
