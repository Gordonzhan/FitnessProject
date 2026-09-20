package com.fitness.service;

import com.fitness.entity.User;
import com.fitness.mapper.UserMapper;
import com.fitness.mapper.WeightRecordMapper;
import com.fitness.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLookupCacheTest {
    @Mock UserMapper userMapper;
    @Mock WeightRecordMapper weightRecordMapper;
    @InjectMocks UserServiceImpl service;

    @Test
    void repeatedLookupUsesCacheAndReturnsDefensiveCopies() {
        User stored = user(1L, 70);
        when(userMapper.findById(1L)).thenReturn(stored);

        User first = service.findById(1L);
        first.setAge(99);
        User second = service.findById(1L);

        assertNotSame(first, second);
        assertEquals(70, second.getAge());
        verify(userMapper, times(1)).findById(1L);
    }

    @Test
    void successfulUpdateRefreshesCachedProfile() {
        User original = user(1L, 70);
        when(userMapper.findById(1L)).thenReturn(original);
        service.findById(1L);

        User updated = user(1L, 71);
        service.updateUser(updated);

        assertEquals(71, service.findById(1L).getAge());
        verify(userMapper, times(1)).findById(1L);
    }

    private User user(long id, int age) {
        User user = new User();
        user.setId(id);
        user.setAge(age);
        return user;
    }
}
