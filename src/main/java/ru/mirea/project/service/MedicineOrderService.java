package ru.mirea.project.service;

import ru.mirea.project.model.MedicineOrder;
import ru.mirea.project.model.OrderStatus;
import ru.mirea.project.model.OrderStatistics;
import ru.mirea.project.model.PaymentMethod;

import java.util.Comparator;
import java.util.List;

/**
 * Интерфейс сервиса управления заказами доставки лекарств.
 */
public interface MedicineOrderService {
    MedicineOrder createOrder(MedicineOrder order);
    MedicineOrder getOrderById(Long id);
    MedicineOrder getOrderByNumber(String orderNumber);
    List<MedicineOrder> getAllOrders();
    MedicineOrder updateOrder(MedicineOrder order);
    void deleteOrder(Long id);
    MedicineOrder changeOrderStatus(Long id, OrderStatus newStatus);


    List<MedicineOrder> searchByKeyword(String keyword);
    List<MedicineOrder> searchByClientName(String clientName);
    List<MedicineOrder> searchByPhone(String phone);

    List<MedicineOrder> filterByStatus(OrderStatus status);
    List<MedicineOrder> filterByPrescription(boolean prescriptionRequired);
    List<MedicineOrder> filterByAmountRange(double minAmount, double maxAmount);
    List<MedicineOrder> filterByPaymentMethod(PaymentMethod paymentMethod);

    List<MedicineOrder> getSortedOrders(Comparator<MedicineOrder> comparator);

    OrderStatistics calculateStatistics();
}
