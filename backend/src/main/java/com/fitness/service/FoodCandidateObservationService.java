package com.fitness.service;

import com.fitness.mapper.FoodDatabaseCandidateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** 记录待审核名称；该服务不创建或修改任何正式 food_database 记录。 */
@Service
public class FoodCandidateObservationService {
    private final FoodDatabaseCandidateMapper mapper;

    public FoodCandidateObservationService(FoodDatabaseCandidateMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public void observe(List<Observation> observations) {
        observations.forEach(item -> {
            String normalized = normalize(item.recognizedName());
            if (!normalized.isEmpty()) {
                mapper.observe(item.recognizedName().trim(), normalized, item.cookingMethod());
            }
        });
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。；、（）()]+", "");
    }

    public record Observation(String recognizedName, String cookingMethod) {}
}
