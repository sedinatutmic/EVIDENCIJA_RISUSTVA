package org.example.service;

import org.example.db.dao.UserDao;
import org.example.db.impl.JdbcUserDao;
import org.example.model.User;

import java.util.List;
import java.util.Optional;

public class UserService {

    private final UserDao userDao;

    public UserService() {
        this.userDao = new JdbcUserDao();
    }

    // package-private constructor for tests
    UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public List<User> listUsers() throws Exception {
        return userDao.findAll();
    }

    public Optional<User> getUser(long id) throws Exception {
        return userDao.findById(id);
    }

    public Optional<User> findByQr(String qrValue) throws Exception {
        return userDao.findByQr(qrValue);
    }

    public User createUser(User user) throws Exception {
        validateUserForCreate(user);
        // ensure qrValue uniqueness
        Optional<User> existing = userDao.findByQr(user.getQrValue());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("QR value already exists");
        }
        return userDao.create(user);
    }

    public boolean updateUser(User user) throws Exception {
        validateUserForUpdate(user);
        // ensure if QR changed it doesn't conflict
        Optional<User> byQr = userDao.findByQr(user.getQrValue());
        if (byQr.isPresent() && byQr.get().getId() != user.getId()) {
            throw new IllegalArgumentException("QR value already exists");
        }
        return userDao.update(user);
    }

    public boolean deleteUser(long id) throws Exception {
        return userDao.delete(id);
    }

    private void validateUserForCreate(User user) {
        if (user == null) throw new IllegalArgumentException("User is null");
        if (user.getQrValue() == null || user.getQrValue().isBlank()) {
            throw new IllegalArgumentException("QR value is required");
        }
        if (user.getFullName() == null || user.getFullName().isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
    }

    private void validateUserForUpdate(User user) {
        if (user == null) throw new IllegalArgumentException("User is null");
        if (user.getId() <= 0) throw new IllegalArgumentException("User id is required for update");
        validateUserForCreate(user);
    }
}
