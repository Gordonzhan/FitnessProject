package com.fitness.service;

import com.fitness.common.PageResult;
import com.fitness.entity.FoodDatabase;
import com.fitness.repository.ExerciseDatabaseRepository;
import com.fitness.repository.FoodDatabaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseServiceTest {
    @Mock
    private FoodDatabaseRepository foodRepository;
    @Mock
    private ExerciseDatabaseRepository exerciseRepository;
    @InjectMocks
    private DatabaseService service;

    @Test
    void searchTrimsKeywordAndCapsPageSize() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(foodRepository.searchEnabled(eq("鸡胸"), eq("肉禽水产"), pageable.capture()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(2), 75));

        PageResult<FoodDatabase> result = service.searchFoods("  鸡胸  ", " 肉禽水产 ", -2, 500);

        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(50, pageable.getValue().getPageSize());
        assertEquals(75, result.getTotal());
        assertTrue(result.isHasMore());
    }

    @Test
    void pageResultMarksLastPage() {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(foodRepository.searchEnabled(eq(""), eq(""), pageable.capture()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(new FoodDatabase()), invocation.getArgument(2), 21));

        PageResult<FoodDatabase> result = service.searchFoods(null, null, 1, 20);

        assertFalse(result.isHasMore());
        assertEquals(1, result.getItems().size());
    }

    @Test
    void resolveSkipsDatabaseForEmptyNames() {
        assertTrue(service.resolveFoods(List.of("", "  ")).isEmpty());
        assertTrue(service.resolveExercises(null).isEmpty());
        verify(foodRepository, never()).findByFoodNameInAndEnabledTrue(anyList());
        verify(exerciseRepository, never()).findByNameInAndEnabledTrue(anyList());
    }

    @Test
    void returnsEnabledCategories() {
        when(foodRepository.findEnabledCategories()).thenReturn(List.of("水果", "蔬菜"));
        when(exerciseRepository.findEnabledCategories()).thenReturn(List.of("力量训练", "跑步"));

        assertEquals(List.of("水果", "蔬菜"), service.getFoodCategories());
        assertEquals(List.of("力量训练", "跑步"), service.getExerciseCategories());
    }

    @Test
    void categoryListsAreCachedAndFoodSaveInvalidatesOnlyFoodCache() {
        FoodDatabase saved = new FoodDatabase();
        when(foodRepository.findEnabledCategories())
                .thenReturn(List.of("水果"), List.of("水果", "蔬菜"));
        when(exerciseRepository.findEnabledCategories()).thenReturn(List.of("力量训练"));
        when(foodRepository.save(saved)).thenReturn(saved);

        assertEquals(List.of("水果"), service.getFoodCategories());
        assertEquals(List.of("水果"), service.getFoodCategories());
        assertEquals(List.of("力量训练"), service.getExerciseCategories());
        service.saveFood(saved);
        assertEquals(List.of("水果", "蔬菜"), service.getFoodCategories());
        assertEquals(List.of("力量训练"), service.getExerciseCategories());

        verify(foodRepository, times(2)).findEnabledCategories();
        verify(exerciseRepository, times(1)).findEnabledCategories();
    }
}
