package org.example.db.dao;

import org.example.model.User;

import java.util.List;
import java.util.Optional;

public interface UserDao {
    Optional<User> findByQr(String qrValue) throws Exception;

    List<User> findAll() throws Exception;

    Optional<User> findById(long id) throws Exception;

    User create(User user) throws Exception;

    boolean update(User user) throws Exception;

    boolean delete(long id) throws Exception;

    // new helpers for profile assets
    boolean updateProfileImagePath(long userId, String path) throws Exception;

    boolean updateCvFilePath(long userId, String path) throws Exception;
}
