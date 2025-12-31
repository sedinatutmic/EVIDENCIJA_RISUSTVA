package org.example.db.dao;

import org.example.model.User;

import java.util.Optional;

public interface UserDao {
    Optional<User> findByQr(String qrValue) throws Exception;
}

