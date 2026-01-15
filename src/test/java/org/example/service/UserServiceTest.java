package org.example.service;

import org.example.db.dao.UserDao;
import org.example.model.User;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class UserServiceTest {

    @Test
    public void createUserHappyPath() throws Exception {
        UserDao dao = Mockito.mock(UserDao.class);
        when(dao.findByQr("qr1")).thenReturn(Optional.empty());
        when(dao.create(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(123L);
            return u;
        });

        UserService svc = new UserService(dao);
        User u = new User();
        u.setFullName("Test User");
        u.setEmail("t@example.com");
        u.setQrValue("qr1");
        u.setActive(true);

        User created = svc.createUser(u);
        assertNotNull(created);
        assertEquals(123L, created.getId());
    }

    @Test
    public void createUserValidationFails() {
        UserDao dao = Mockito.mock(UserDao.class);
        UserService svc = new UserService(dao);
        User u = new User();
        u.setFullName("");
        u.setQrValue("");

        assertThrows(IllegalArgumentException.class, () -> svc.createUser(u));
    }
}

