package ru.mirea.project.repository;

import ru.mirea.project.model.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends CrudRepository<User, Long> {
    Optional<User> findByPhone(String phone);
    Optional<User> findByEmail(String email);
    List<User> searchByName(String namePart);
}
