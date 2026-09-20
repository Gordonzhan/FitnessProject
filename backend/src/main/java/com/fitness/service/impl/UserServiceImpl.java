package com.fitness.service.impl;

import com.fitness.entity.User;
import com.fitness.entity.WeightRecord;
import com.fitness.mapper.UserMapper;
import com.fitness.mapper.WeightRecordMapper;
import com.fitness.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeightRecordMapper weightRecordMapper;
    @Value("${fitness.business-zone:Asia/Shanghai}")
    private String businessZone = "Asia/Shanghai";
    @Value("${fitness.performance.user-cache-ttl-seconds:30}")
    private long userCacheTtlSeconds = 30;
    @Value("${fitness.performance.user-cache-max-size:10000}")
    private int userCacheMaxSize = 10000;
    private final ConcurrentHashMap<Long, CachedUser> usersById = new ConcurrentHashMap<>();
    
    @Override
    public User getUserByOpenid(String openid) {
        return userMapper.findByOpenid(openid);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User saveUser(User user) {
        userMapper.insert(user);
        insertWeightRecord(user.getId(), today(), user.getWeight(), "PROFILE");
        cache(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateUser(User user) {
        return updateUser(user, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateUser(User user, LocalDate weightRecordDate) {
        LocalDate recordDate = weightRecordDate == null ? today() : weightRecordDate;
        if (recordDate.isAfter(today())) throw new IllegalArgumentException("体重记录日期不能晚于今天");
        userMapper.update(user);
        insertWeightRecord(user.getId(), recordDate, user.getWeight(), "PROFILE");
        cache(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateCurrentWeight(Long userId, BigDecimal weight) {
        if (userMapper.updateWeight(userId, weight) == 0) throw new IllegalArgumentException("用户档案不存在");
        User user = userMapper.findById(userId);
        cache(user);
        return copyUser(user);
    }
    
    @Override
    public User findById(Long id) {
        if (id == null) return null;
        CachedUser cached = usersById.get(id);
        long now = System.nanoTime();
        if (cached != null && cached.expiresAtNanos() > now) return copyUser(cached.user());
        if (cached != null) usersById.remove(id, cached);

        User user = userMapper.findById(id);
        cache(user);
        return copyUser(user);
    }
    
    @Override
    public double calculateDailyCalories(User user) {
        // 计算 BMR
        double bmr;
        double weight = user.getWeight().doubleValue();
        double height = user.getHeight().doubleValue();
        double activityLevel = user.getActivityLevel().doubleValue();
        
        if ("male".equals(user.getGender())) {
            bmr = 10 * weight + 6.25 * height - 5 * user.getAge() + 5;
        } else {
            bmr = 10 * weight + 6.25 * height - 5 * user.getAge() - 161;
        }
        
        // 计算 TDEE
        double tdee = bmr * activityLevel;
        
        // 根据目标调整
        if ("lose_weight".equals(user.getGoal())) {
            return tdee - 500;
        } else if ("gain_muscle".equals(user.getGoal())) {
            return tdee + 300;
        } else {
            return tdee;
        }
    }

    /** 将用户副本写入有界短时缓存，避免鉴权流程频繁读取数据库。 */
    private void cache(User user) {
        if (user == null || user.getId() == null || userCacheTtlSeconds <= 0 || userCacheMaxSize <= 0) return;
        if (usersById.size() >= userCacheMaxSize && !usersById.containsKey(user.getId())) {
            // 用户资料缓存只是减轻鉴权热读；达到上限时整体清空，避免无界占用内存。
            usersById.clear();
        }
        usersById.put(user.getId(), new CachedUser(copyUser(user),
                System.nanoTime() + TimeUnit.SECONDS.toNanos(userCacheTtlSeconds)));
    }

    /** 创建用户实体的防御性副本，防止调用方修改缓存中的共享对象。 */
    private User copyUser(User source) {
        if (source == null) return null;
        User copy = new User();
        copy.setId(source.getId());
        copy.setOpenid(source.getOpenid());
        copy.setDisplayName(source.getDisplayName());
        copy.setGender(source.getGender());
        copy.setAge(source.getAge());
        copy.setHeight(source.getHeight());
        copy.setWeight(source.getWeight());
        copy.setGoal(source.getGoal());
        copy.setActivityLevel(source.getActivityLevel());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private void insertWeightRecord(Long userId, LocalDate date, BigDecimal weight, String source) {
        WeightRecord record = new WeightRecord();
        record.setUserId(userId);
        record.setRecordDate(date);
        record.setWeight(weight);
        record.setSource(source);
        weightRecordMapper.insert(record);
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(businessZone));
    }

    /** 缓存中的用户快照及其基于单调时钟的失效时间。 */
    private record CachedUser(User user, long expiresAtNanos) {}
}
