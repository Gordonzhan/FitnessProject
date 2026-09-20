package com.fitness.service;

import com.fitness.entity.User;
import com.fitness.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserServiceTest {
    private final UserService userService = new UserServiceImpl();
    
    @Test
    public void testCalculateDailyCalories() {
        User user = new User();
        user.setGender("male");
        user.setAge(25);
        user.setHeight(new BigDecimal(175.0));
        user.setWeight(new BigDecimal(70.0));
        user.setActivityLevel(new BigDecimal(1.375));
        user.setGoal("maintain");
        
        double dailyCalories = userService.calculateDailyCalories(user);
        
        // 计算BMR: 10*70 + 6.25*175 - 5*25 + 5 = 700 + 1093.75 - 125 + 5 = 1673.75
        // TDEE: 1673.75 * 1.375 = 2301.40625
        assertEquals(2301.40625, dailyCalories, 0.001);
    }
    
    @Test
    public void testCalculateDailyCaloriesLoseWeight() {
        User user = new User();
        user.setGender("male");
        user.setAge(25);
        user.setHeight(new BigDecimal(175.0));
        user.setWeight(new BigDecimal(70.0));
        user.setActivityLevel(new BigDecimal(1.375));
        user.setGoal("lose_weight");
        
        double dailyCalories = userService.calculateDailyCalories(user);
        
        // 2301.40625 - 500 = 1801.40625
        assertEquals(1801.40625, dailyCalories, 0.001);
    }
    
    @Test
    public void testCalculateDailyCaloriesGainMuscle() {
        User user = new User();
        user.setGender("male");
        user.setAge(25);
        user.setHeight(new BigDecimal(175.0));
        user.setWeight(new BigDecimal(70.0));
        user.setActivityLevel(new BigDecimal(1.375));
        user.setGoal("gain_muscle");
        
        double dailyCalories = userService.calculateDailyCalories(user);
        
        // 2301.40625 + 300 = 2601.40625
        assertEquals(2601.40625, dailyCalories, 0.001);
    }
}
