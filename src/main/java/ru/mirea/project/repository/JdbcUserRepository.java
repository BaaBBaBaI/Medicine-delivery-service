package ru.mirea.project.repository;

import ru.mirea.project.exception.DatabaseException;
import ru.mirea.project.model.User;
import ru.mirea.project.util.DatabaseManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcUserRepository implements UserRepository {

    private final DatabaseManager dbManager;

    public JdbcUserRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public JdbcUserRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public User save(User user) {
        String sql = "INSERT INTO users (full_name, phone, email, default_address, registered_at) " +
                     "VALUES (?, ?, ?, ?, ?) RETURNING id";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getFullName());
            stmt.setString(2, user.getPhone());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getDefaultAddress());
            stmt.setTimestamp(5, Timestamp.valueOf(user.getRegisteredAt() != null ? user.getRegisteredAt() : LocalDateTime.now()));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    user.setId(rs.getLong(1));
                    return user;
                }
            }
            throw new DatabaseException("Не удалось получить сгенерированный ID пользователя");
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка сохранения пользователя в БД: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<User> findById(Long id) {
        String sql = "SELECT id, full_name, phone, email, default_address, registered_at FROM users WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска пользователя по ID=" + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT id, full_name, phone, email, default_address, registered_at FROM users ORDER BY id ASC";
        List<User> users = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                users.add(mapRowToUser(rs));
            }
            return users;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка получения списка пользователей: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean update(User user) {
        String sql = "UPDATE users SET full_name = ?, phone = ?, email = ?, default_address = ? WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getFullName());
            stmt.setString(2, user.getPhone());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, user.getDefaultAddress());
            stmt.setLong(5, user.getId());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка обновления пользователя ID=" + user.getId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteById(Long id) {
        String sql = "DELETE FROM users WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState())) {
                throw new DatabaseException("Невозможно удалить клиента, так как за ним числятся заказы в системе.", e);
            }
            throw new DatabaseException("Ошибка удаления пользователя ID=" + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM users";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка подсчета пользователей: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        String sql = "SELECT id, full_name, phone, email, default_address, registered_at FROM users WHERE phone = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, phone);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска пользователя по телефону: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT id, full_name, phone, email, default_address, registered_at FROM users WHERE LOWER(email) = LOWER(?)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToUser(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска пользователя по email: " + e.getMessage(), e);
        }
    }

    @Override
    public List<User> searchByName(String namePart) {
        String sql = "SELECT id, full_name, phone, email, default_address, registered_at FROM users " +
                     "WHERE LOWER(full_name) LIKE LOWER(?) OR LOWER(phone) LIKE LOWER(?) OR LOWER(email) LIKE LOWER(?) " +
                     "ORDER BY id ASC";
        List<User> users = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String pattern = "%" + namePart + "%";
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(mapRowToUser(rs));
                }
            }
            return users;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска пользователей: " + e.getMessage(), e);
        }
    }

    private User mapRowToUser(ResultSet rs) throws SQLException {
        Timestamp regTs = rs.getTimestamp("registered_at");
        return new User(
                rs.getLong("id"),
                rs.getString("full_name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("default_address"),
                regTs != null ? regTs.toLocalDateTime() : null
        );
    }
}
