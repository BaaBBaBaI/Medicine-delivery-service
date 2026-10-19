package ru.mirea.project.repository;

import ru.mirea.project.model.MedicineOrder;
import ru.mirea.project.model.OrderStatus;

import java.util.List;
import java.util.Optional;

public interface MedicineOrderRepository extends CrudRepository<MedicineOrder, Long> {
    Optional<MedicineOrder> findByOrderNumber(String orderNumber);
    List<MedicineOrder> findByClientId(Long clientId);
    List<MedicineOrder> findByStatus(OrderStatus status);
    List<MedicineOrder> searchByKeyword(String keyword);
    boolean updateStatus(Long orderId, OrderStatus newStatus);
}
