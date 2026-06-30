package com.server.service;

import com.server.model.User;
import com.server.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserNameServiceTest {

    private UserNameService userNameService;
    private UserRepository mockRepo;

    @BeforeEach
    void setUp() throws Exception {
        userNameService = new UserNameService();
        mockRepo = mock(UserRepository.class);

        Field repoField = UserNameService.class.getDeclaredField("userRepository");
        repoField.setAccessible(true);
        repoField.set(userNameService, mockRepo);
    }

    @Test
    void testUpdateUsernameSuccess() {
        User user = new User();
        user.setId(1L);
        user.setUsername("oldname");
        when(mockRepo.findById(1L)).thenReturn(user);
        when(mockRepo.findByUsername("newname")).thenReturn(null);

        boolean result = userNameService.updateUsername(1L, "newname");
        assertTrue(result);
    }

    @Test
    void testUpdateUsernameUserNotFound() {
        when(mockRepo.findById(999L)).thenReturn(null);

        boolean result = userNameService.updateUsername(999L, "newname");
        assertFalse(result);
    }

    @Test
    void testUpdateUsernameSameName() {
        User user = new User();
        user.setId(1L);
        user.setUsername("samenick");
        when(mockRepo.findById(1L)).thenReturn(user);

        boolean result = userNameService.updateUsername(1L, "samenick");
        assertFalse(result);
        verify(mockRepo, never()).findByUsername(anyString());
    }

    @Test
    void testUpdateUsernameAlreadyTaken() {
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("current");
        when(mockRepo.findById(1L)).thenReturn(currentUser);

        User existingUser = new User();
        existingUser.setId(2L);
        existingUser.setUsername("taken");
        when(mockRepo.findByUsername("taken")).thenReturn(existingUser);

        boolean result = userNameService.updateUsername(1L, "taken");
        assertFalse(result);
    }
}
