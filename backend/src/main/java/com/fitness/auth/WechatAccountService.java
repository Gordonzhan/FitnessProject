package com.fitness.auth;

import com.fitness.entity.User;
import com.fitness.service.UserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** 将微信身份映射为项目账户，并保证首次注册在并发请求下保持幂等。 */
@Service
public class WechatAccountService {
    private final UserService userService;

    public WechatAccountService(UserService userService) {
        this.userService = userService;
    }

    public User getOrCreate(String openid) {
        if (openid == null || openid.isBlank()) {
            throw new IllegalArgumentException("微信用户标识不能为空");
        }

        User existing = userService.getUserByOpenid(openid);
        if (existing != null) return existing;

        try {
            // saveUser 在独立的事务代理中完成用户和初始体重记录写入。
            return userService.saveUser(defaultUser(openid));
        } catch (DuplicateKeyException duplicate) {
            // 两个请求同时首登时，users.openid 唯一索引只允许一个成功。
            // 写事务已在返回此处前结束，因此重新查询可以稳定读到胜出的账户。
            User concurrentWinner = userService.getUserByOpenid(openid);
            if (concurrentWinner != null) return concurrentWinner;
            throw duplicate;
        }
    }

    private User defaultUser(String openid) {
        User user = new User();
        user.setOpenid(openid);
        user.setGender("male");
        user.setAge(25);
        user.setHeight(new BigDecimal("175"));
        user.setWeight(new BigDecimal("70"));
        user.setGoal("maintain");
        user.setActivityLevel(new BigDecimal("1.375"));
        return user;
    }
}
