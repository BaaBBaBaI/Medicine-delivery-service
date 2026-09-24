package ru.mirea.project.service;

import ru.mirea.project.model.User;

import java.util.List;

/**
 * Интерфейс сервиса управления клиентами/пользователями.
 */
public interface UserService {
    User registerUser(User user);
    User getUserById(Long id);
    List<User> getAllUsers();
    User updateUser(User user);
    void deleteUser(Long id);
    List<User> searchUsersByName(String name);
}
