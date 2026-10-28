package ru.mirea.project.service;

import ru.mirea.project.exception.BusinessException;
import ru.mirea.project.exception.EntityNotFoundException;
import ru.mirea.project.model.User;
import ru.mirea.project.repository.UserRepository;

import java.util.List;

public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User registerUser(User user) {
        validateUser(user);

        userRepository.findByPhone(user.getPhone().trim()).ifPresent(existing -> {
            throw new BusinessException("Клиент с номером телефона '" + user.getPhone() + "' уже зарегистрирован.");
        });

        userRepository.findByEmail(user.getEmail().trim()).ifPresent(existing -> {
            throw new BusinessException("Клиент с email '" + user.getEmail() + "' уже зарегистрирован.");
        });

        user.setFullName(user.getFullName().trim());
        user.setPhone(user.getPhone().trim());
        user.setEmail(user.getEmail().trim());
        user.setDefaultAddress(user.getDefaultAddress().trim());

        return userRepository.save(user);
    }

    @Override
    public User getUserById(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("ID клиента должен быть положительным числом.");
        }
        if (id > 1_000_000_000L) {
            throw new BusinessException("ID клиента не должен превышать 1 000 000 000.");
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
        if (user.getId() == null || user.getId() <= 0) {
            throw new BusinessException("Для обновления клиента необходимо передать корректный ID.");
        }
        if (user.getId() > 1_000_000_000L) {
            throw new BusinessException("ID клиента не должен превышать 1 000 000 000.");
        }
        validateUser(user);
        getUserById(user.getId());

        userRepository.findByPhone(user.getPhone().trim()).ifPresent(other -> {
            if (!other.getId().equals(user.getId())) {
                throw new BusinessException("Телефон '" + user.getPhone() + "' уже используется другим клиентом.");
            }
        });

        userRepository.findByEmail(user.getEmail().trim()).ifPresent(other -> {
            if (!other.getId().equals(user.getId())) {
                throw new BusinessException("Email '" + user.getEmail() + "' уже используется другим клиентом.");
            }
        });

        user.setFullName(user.getFullName().trim());
        user.setPhone(user.getPhone().trim());
        user.setEmail(user.getEmail().trim());
        user.setDefaultAddress(user.getDefaultAddress().trim());

        boolean updated = userRepository.update(user);
        if (!updated) {
            throw new BusinessException("Не удалось обновить данные клиента ID=" + user.getId());
        }
        return user;
    }

    @Override
    public void deleteUser(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException("ID клиента должен быть положительным числом.");
        }
        getUserById(id);
        boolean deleted = userRepository.deleteById(id);
        if (!deleted) {
            throw new BusinessException("Не удалось удалить клиента ID=" + id + ". Возможно, за ним закреплены заказы.");
        }
    }

    @Override
    public List<User> searchUsersByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return getAllUsers();
        }
        if (name.trim().length() > 150) {
            throw new BusinessException("Поисковый запрос не должен превышать 150 символов.");
        }
        return userRepository.searchByName(name.trim());
    }

    private void validateUser(User user) {
        if (user == null) {
            throw new BusinessException("Объект клиента не может быть пустым.");
        }
        if (user.getFullName() == null || user.getFullName().trim().length() < 2) {
            throw new BusinessException("Поле «ФИО клиента» обязательно и должно содержать не менее 2 символов.");
        }
        if (user.getFullName().trim().length() > 150) {
            throw new BusinessException("Поле «ФИО клиента» не должно превышать 150 символов.");
        }
        if (user.getPhone() == null || user.getPhone().trim().length() < 6) {
            throw new BusinessException("Поле «Контактный телефон» обязательно (не менее 6 символов).");
        }
        if (user.getPhone().trim().length() > 25) {
            throw new BusinessException("Поле «Контактный телефон» не должно превышать 25 символов.");
        }
        if (user.getEmail() == null || !user.getEmail().contains("@") || user.getEmail().trim().length() < 5) {
            throw new BusinessException("Поле «Email» заполнено некорректно: укажите адрес электронной почты с символом '@'.");
        }
        if (user.getEmail().trim().length() > 100) {
            throw new BusinessException("Поле «Email» не должно превышать 100 символов.");
        }
        if (user.getDefaultAddress() == null || user.getDefaultAddress().trim().length() < 5) {
            throw new BusinessException("Поле «Адрес по умолчанию» обязательно (не менее 5 символов).");
        }
        if (user.getDefaultAddress().trim().length() > 255) {
            throw new BusinessException("Поле «Адрес по умолчанию» не должно превышать 255 символов.");
        }
    }
}
