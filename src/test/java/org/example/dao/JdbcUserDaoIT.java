package org.example.dao;

import org.example.db.impl.JdbcUserDao;
import org.example.model.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class JdbcUserDaoIT {

    @Test
    public void crudCycle() throws Exception {
        JdbcUserDao dao = new JdbcUserDao();

        // initial count
        List<User> before = dao.findAll();

        User u = new User();
        u.setFullName("IT Test User");
        u.setEmail("it@example.com");
        u.setQrValue("it-qr-12345");
        u.setActive(true);

        User created = dao.create(u);
        assertTrue(created.getId() > 0);

        Optional<User> fetched = dao.findById(created.getId());
        assertTrue(fetched.isPresent());
        assertEquals("IT Test User", fetched.get().getFullName());

        created.setFullName("IT Test User Updated");
        boolean updated = dao.update(created);
        assertTrue(updated);

        Optional<User> fetched2 = dao.findById(created.getId());
        assertTrue(fetched2.isPresent());
        assertEquals("IT Test User Updated", fetched2.get().getFullName());

        boolean deleted = dao.delete(created.getId());
        assertTrue(deleted);

        List<User> after = dao.findAll();
        assertEquals(before.size(), after.size());
    }
}

