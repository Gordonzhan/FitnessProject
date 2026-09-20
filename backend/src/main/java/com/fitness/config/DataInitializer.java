package com.fitness.config;

import com.fitness.repository.ExerciseDatabaseRepository;
import com.fitness.repository.FoodDatabaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 只检查基础数据库是否准备好，不在 Java 代码中维护另一份默认数据。
 * 初始数据统一由 SQL 初始化/迁移脚本或后续的后台管理功能维护。
 */
@Component
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final FoodDatabaseRepository foodDatabaseRepository;
    private final ExerciseDatabaseRepository exerciseDatabaseRepository;

    public DataInitializer(FoodDatabaseRepository foodDatabaseRepository,
                           ExerciseDatabaseRepository exerciseDatabaseRepository) {
        this.foodDatabaseRepository = foodDatabaseRepository;
        this.exerciseDatabaseRepository = exerciseDatabaseRepository;
    }

    @Override
    public void run(String... args) {
        if (foodDatabaseRepository.count() == 0) {
            log.warn("食物基础数据库为空，请执行 database.sql 及后续基础数据迁移脚本");
        }
        if (exerciseDatabaseRepository.count() == 0) {
            log.warn("训练动作基础数据库为空，请执行 database.sql 及后续基础数据迁移脚本");
        }
    }
}
