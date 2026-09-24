package ru.mirea.project.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Сущность клиента / пользователя системы доставки лекарств.
 * Демонстрирует инкапсуляцию: private-поля, геттеры/сеттеры, конструкторы.
 */
public class User {
    private Long id;
    private String fullName;
    private String phone;
    private String email;
    private String defaultAddress;
    private LocalDateTime registeredAt;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public User() {
        this.registeredAt = LocalDateTime.now();
    }

    public User(Long id, String fullName, String phone, String email, String defaultAddress, LocalDateTime registeredAt) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.email = email;
        this.defaultAddress = defaultAddress;
        this.registeredAt = registeredAt != null ? registeredAt : LocalDateTime.now();
    }

    public User(String fullName, String phone, String email, String defaultAddress) {
        this(null, fullName, phone, email, defaultAddress, LocalDateTime.now());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDefaultAddress() {
        return defaultAddress;
    }

    public void setDefaultAddress(String defaultAddress) {
        this.defaultAddress = defaultAddress;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("User[ID=%d, Имя='%s', Телефон='%s', Email='%s', Адрес='%s', Регистрация=%s]",
                id, fullName, phone, email, defaultAddress, 
                registeredAt != null ? registeredAt.format(FORMATTER) : "—");
    }
}
