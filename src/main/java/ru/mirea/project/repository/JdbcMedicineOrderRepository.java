package ru.mirea.project.repository;

import ru.mirea.project.exception.DatabaseException;
import ru.mirea.project.model.MedicineOrder;
import ru.mirea.project.model.OrderStatus;
import ru.mirea.project.model.PaymentMethod;
import ru.mirea.project.model.User;
import ru.mirea.project.util.DatabaseManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcMedicineOrderRepository implements MedicineOrderRepository {

    private final DatabaseManager dbManager;

    private static final String SELECT_BASE =
            "SELECT o.id, o.order_number, o.client_id, o.delivery_address, o.contact_phone, " +
            "       o.total_amount, o.status, o.payment_method, o.prescription_required, " +
            "       o.prescription_number, o.delivery_notes, o.created_at, o.delivered_at, " +
            "       u.full_name as u_name, u.phone as u_phone, u.email as u_email, " +
            "       u.default_address as u_address, u.registered_at as u_registered " +
            "FROM medicine_orders o " +
            "JOIN users u ON o.client_id = u.id ";

    public JdbcMedicineOrderRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public JdbcMedicineOrderRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    @Override
    public MedicineOrder save(MedicineOrder order) {
        String sql = "INSERT INTO medicine_orders (order_number, client_id, delivery_address, contact_phone, " +
                     "total_amount, status, payment_method, prescription_required, prescription_number, " +
                     "delivery_notes, created_at, delivered_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, order.getOrderNumber());
            stmt.setLong(2, order.getClientId());
            stmt.setString(3, order.getDeliveryAddress());
            stmt.setString(4, order.getContactPhone());
            stmt.setDouble(5, order.getTotalAmount());
            stmt.setString(6, order.getStatus().name());
            stmt.setString(7, order.getPaymentMethod().name());
            stmt.setBoolean(8, order.isPrescriptionRequired());
            stmt.setString(9, order.isPrescriptionRequired() ? order.getPrescriptionNumber() : null);
            stmt.setString(10, order.getDeliveryNotes());
            stmt.setTimestamp(11, Timestamp.valueOf(order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now()));
            stmt.setTimestamp(12, order.getDeliveredAt() != null ? Timestamp.valueOf(order.getDeliveredAt()) : null);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    order.setId(rs.getLong(1));
                    return order;
                }
            }
            throw new DatabaseException("Не удалось получить сгенерированный ID заказа");
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка сохранения заказа в БД: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<MedicineOrder> findById(Long id) {
        String sql = SELECT_BASE + "WHERE o.id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToOrder(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска заказа ID=" + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<MedicineOrder> findAll() {
        String sql = SELECT_BASE + "ORDER BY o.id ASC";
        List<MedicineOrder> orders = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                orders.add(mapRowToOrder(rs));
            }
            return orders;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка получения всех заказов: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean update(MedicineOrder order) {
        String sql = "UPDATE medicine_orders SET client_id = ?, delivery_address = ?, contact_phone = ?, " +
                     "total_amount = ?, status = ?, payment_method = ?, prescription_required = ?, " +
                     "prescription_number = ?, delivery_notes = ?, delivered_at = ? WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, order.getClientId());
            stmt.setString(2, order.getDeliveryAddress());
            stmt.setString(3, order.getContactPhone());
            stmt.setDouble(4, order.getTotalAmount());
            stmt.setString(5, order.getStatus().name());
            stmt.setString(6, order.getPaymentMethod().name());
            stmt.setBoolean(7, order.isPrescriptionRequired());
            stmt.setString(8, order.isPrescriptionRequired() ? order.getPrescriptionNumber() : null);
            stmt.setString(9, order.getDeliveryNotes());
            stmt.setTimestamp(10, order.getDeliveredAt() != null ? Timestamp.valueOf(order.getDeliveredAt()) : null);
            stmt.setLong(11, order.getId());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка обновления заказа ID=" + order.getId() + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean deleteById(Long id) {
        String sql = "DELETE FROM medicine_orders WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка удаления заказа ID=" + id + ": " + e.getMessage(), e);
        }
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM medicine_orders";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка подсчета заказов: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<MedicineOrder> findByOrderNumber(String orderNumber) {
        String sql = SELECT_BASE + "WHERE o.order_number = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, orderNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToOrder(rs));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска заказа по номеру: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MedicineOrder> findByClientId(Long clientId) {
        String sql = SELECT_BASE + "WHERE o.client_id = ? ORDER BY o.id DESC";
        List<MedicineOrder> orders = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, clientId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapRowToOrder(rs));
                }
            }
            return orders;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска заказов клиента ID=" + clientId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public List<MedicineOrder> findByStatus(OrderStatus status) {
        String sql = SELECT_BASE + "WHERE o.status = ? ORDER BY o.id DESC";
        List<MedicineOrder> orders = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status.name());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapRowToOrder(rs));
                }
            }
            return orders;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска заказов по статусу: " + e.getMessage(), e);
        }
    }

    @Override
    public List<MedicineOrder> searchByKeyword(String keyword) {
        String sql = SELECT_BASE +
                     "WHERE LOWER(o.order_number) LIKE LOWER(?) " +
                     "   OR LOWER(COALESCE(o.delivery_notes, '')) LIKE LOWER(?) " +
                     "   OR LOWER(o.delivery_address) LIKE LOWER(?) " +
                     "   OR LOWER(u.full_name) LIKE LOWER(?) " +
                     "   OR LOWER(o.contact_phone) LIKE LOWER(?) " +
                     "   OR LOWER(COALESCE(o.prescription_number, '')) LIKE LOWER(?) " +
                     "ORDER BY o.id DESC";
        List<MedicineOrder> orders = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            String param = "%" + keyword + "%";
            for (int i = 1; i <= 6; i++) {
                stmt.setString(i, param);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapRowToOrder(rs));
                }
            }
            return orders;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка поиска по ключевому слову: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean updateStatus(Long orderId, OrderStatus newStatus) {
        String sql;
        if (newStatus == OrderStatus.DELIVERED) {
            sql = "UPDATE medicine_orders SET status = ?, delivered_at = CURRENT_TIMESTAMP WHERE id = ?";
        } else {
            sql = "UPDATE medicine_orders SET status = ? WHERE id = ?";
        }

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newStatus.name());
            stmt.setLong(2, orderId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseException("Ошибка обновления статуса заказа ID=" + orderId + ": " + e.getMessage(), e);
        }
    }

    private MedicineOrder mapRowToOrder(ResultSet rs) throws SQLException {
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp deliveredTs = rs.getTimestamp("delivered_at");

        Timestamp clientRegTs = rs.getTimestamp("u_registered");
        User client = new User(
                rs.getLong("client_id"),
                rs.getString("u_name"),
                rs.getString("u_phone"),
                rs.getString("u_email"),
                rs.getString("u_address"),
                clientRegTs != null ? clientRegTs.toLocalDateTime() : null
        );

        MedicineOrder order = new MedicineOrder(
                rs.getLong("id"),
                rs.getString("order_number"),
                rs.getLong("client_id"),
                rs.getString("delivery_address"),
                rs.getString("contact_phone"),
                rs.getDouble("total_amount"),
                OrderStatus.valueOf(rs.getString("status")),
                PaymentMethod.valueOf(rs.getString("payment_method")),
                rs.getBoolean("prescription_required"),
                rs.getString("prescription_number"),
                rs.getString("delivery_notes"),
                createdTs != null ? createdTs.toLocalDateTime() : null,
                deliveredTs != null ? deliveredTs.toLocalDateTime() : null
        );
        order.setClient(client);
        return order;
    }
}
