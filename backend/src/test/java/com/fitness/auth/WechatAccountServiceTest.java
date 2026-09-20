package com.fitness.auth;

import com.fitness.entity.User;
import com.fitness.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WechatAccountServiceTest {
    @Mock UserService users;

    @Test
    void existingWechatUserIsReusedWithoutCreatingAnotherAccount() {
        User existing = user(7L, "openid-existing");
        when(users.getUserByOpenid("openid-existing")).thenReturn(existing);

        User result = new WechatAccountService(users).getOrCreate("openid-existing");

        assertSame(existing, result);
        verify(users, never()).saveUser(any());
    }

    @Test
    void firstLoginCreatesACompleteDefaultProfile() {
        when(users.getUserByOpenid("openid-new")).thenReturn(null);
        when(users.saveUser(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(8L);
            return user;
        });

        User result = new WechatAccountService(users).getOrCreate("openid-new");

        ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
        verify(users).saveUser(created.capture());
        assertEquals(8L, result.getId());
        assertEquals("openid-new", created.getValue().getOpenid());
        assertEquals("male", created.getValue().getGender());
        assertEquals(25, created.getValue().getAge());
        assertEquals(new BigDecimal("175"), created.getValue().getHeight());
        assertEquals(new BigDecimal("70"), created.getValue().getWeight());
        assertEquals("maintain", created.getValue().getGoal());
        assertEquals(new BigDecimal("1.375"), created.getValue().getActivityLevel());
    }

    @Test
    void concurrentFirstLoginReturnsTheAccountThatWonTheUniqueKeyRace() {
        User winner = user(9L, "openid-race");
        when(users.getUserByOpenid("openid-race")).thenReturn(null, winner);
        when(users.saveUser(any())).thenThrow(new DuplicateKeyException("duplicate openid"));

        User result = new WechatAccountService(users).getOrCreate("openid-race");

        assertSame(winner, result);
        verify(users).saveUser(any());
    }

    private User user(long id, String openid) {
        User user = new User();
        user.setId(id);
        user.setOpenid(openid);
        return user;
    }
}
