package ru.mirea.project.service;

import ru.mirea.project.exception.BusinessException;
import ru.mirea.project.exception.EntityNotFoundException;
import ru.mirea.project.model.User;
import ru.mirea.project.repository.UserRepository;

import java.util.List;

/**
 * Реализация бизнес-логики работы с пользователями/клиентами.
 */
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User registerUser(User user) {
        validateUser(user);

        // Проверка уникальности телефон
        userRepository.findByPhone(user.getPhone()).ifPresent(existing -> {
            throw new BusinessException("Клиент с номером телефона '" + user.getPhone() + "' уже зарегистрирован.");
        });

        // Проверка уникальности email
        userRepository.findByEmail(user.getEmail()).ifPresent(existing -> {
            throw new BusinessException("Клиент с email '" + user.getEmail() + "' уже зарегистрирован.");
        });

        return userRepository.save(user);
    }

    @Override
    public User getUserById(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("ID клиента должен быть положительным числом.");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Клиент", id));
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public User updateUser(User user) {
        if (user.getId() == null) {
            throw new BusinessException("Для обновления клиента необходимо передать его ID.");
        }
        validateUser(user);

        User existing = getUserById(user.getId());

        // collision check phone
        userRepository.findByPhone(user.getPhone()).ifPresent(other -> {
            if (!other.getId().equals(user.getId())) {
                throw new BusinessException("Телефон '" + user.getPhone() + "' уже используется другим клиентом.");
            }
        });

        // collision check email
        userRepository.findByEmail(user.getEmail()).ifPresent(other -> {
            if (!other.getId().equals(user.getId())) {
                throw new BusinessException("Email '" + user.getEmail() + "' уже используется другим клиентом.");
            }
        });

        boolean updated = userRepository.update(user);
        if (!updated) {
            throw new BusinessException("Не удалось обновить данные клиента ID=" + user.getId());
        }
        return user;
    }

    @Override
    public void deleteUser(Long id) {
        getUserById(id); 
        boolean deleted = userRepository.deleteById(id);
        if (!deleted) {
            throw new BusinessException("Не удалось удалить клиента ID=" + id + ". Возможно, у него есть активные заказы.");
        }
    }

    @Override
    public List<User> searchUsersByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return getAllUsers();
        }
        return userRepository.searchByName(name.trim());
    }

    private void validateUser(User user) {
        if (user == null) {
            throw new BusinessException("Объект клиента не может быть null.");
        }
        if (user.getFullName() == null || user.getFullName().trim().length() < 2) {
            throw new BusinessException("ФИО клиента обязательно и должно содержать не менее 2 символов.");
        }
        if (user.getPhone() == null || user.getPhone().trim().length() < 6) {
            throw new BusinessException("Контактный телефон обязателен (не менее 6 символов).");
        }
        if (user.getEmail() == null || !user.getEmail().contains("@")) {
            throw new BusinessException("Указан некорректный адрес электронной почты: " + user.getEmail());
        }
        if (user.getDefaultAddress() == null || user.getDefaultAddress().trim().length() < 5) {
            throw new BusinessException("Адрес доставки обязателен (не менее 5 символов).");
        }
    }
}
