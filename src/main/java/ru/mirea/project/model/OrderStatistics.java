package ru.mirea.project.model;

import java.util.Map;

/**
 * Модель аналитических и статистических показателей системы (более 5 показателей по требованиям КР №2).
 */
public class OrderStatistics {
    private final long totalClients;
    private final long totalOrders;
    private final long activeOrdersCount;
    private final long deliveredOrdersCount;
    private final long cancelledOrdersCount;
    private final double totalDeliveredRevenue;
    private final double averageOrderAmount;
    private final double minOrderAmount;
    private final double maxOrderAmount;
    private final long prescriptionOrdersCount;
    private final double prescriptionPercentage;
    private final Map<OrderStatus, Long> ordersByStatus;
    private final Map<PaymentMethod, Long> ordersByPaymentMethod;

    public OrderStatistics(long totalClients, long totalOrders, long activeOrdersCount,
                           long deliveredOrdersCount, long cancelledOrdersCount,
                           double totalDeliveredRevenue, double averageOrderAmount,
                           double minOrderAmount, double maxOrderAmount,
                           long prescriptionOrdersCount, double prescriptionPercentage,
                           Map<OrderStatus, Long> ordersByStatus,
                           Map<PaymentMethod, Long> ordersByPaymentMethod) {
        this.totalClients = totalClients;
        this.totalOrders = totalOrders;
        this.activeOrdersCount = activeOrdersCount;
        this.deliveredOrdersCount = deliveredOrdersCount;
        this.cancelledOrdersCount = cancelledOrdersCount;
        this.totalDeliveredRevenue = totalDeliveredRevenue;
        this.averageOrderAmount = averageOrderAmount;
        this.minOrderAmount = minOrderAmount;
        this.maxOrderAmount = maxOrderAmount;
        this.prescriptionOrdersCount = prescriptionOrdersCount;
        this.prescriptionPercentage = prescriptionPercentage;
        this.ordersByStatus = ordersByStatus;
        this.ordersByPaymentMethod = ordersByPaymentMethod;
    }

    public long getTotalClients() {
        return totalClients;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public long getActiveOrdersCount() {
        return activeOrdersCount;
    }

    public long getDeliveredOrdersCount() {
        return deliveredOrdersCount;
    }

    public long getCancelledOrdersCount() {
        return cancelledOrdersCount;
    }

    public double getTotalDeliveredRevenue() {
        return totalDeliveredRevenue;
    }

    public double getAverageOrderAmount() {
        return averageOrderAmount;
    }

    public double getMinOrderAmount() {
        return minOrderAmount;
    }

    public double getMaxOrderAmount() {
        return maxOrderAmount;
    }

    public long getPrescriptionOrdersCount() {
        return prescriptionOrdersCount;
    }

    public double getPrescriptionPercentage() {
        return prescriptionPercentage;
    }

    public Map<OrderStatus, Long> getOrdersByStatus() {
        return ordersByStatus;
    }

    public Map<PaymentMethod, Long> getOrdersByPaymentMethod() {
        return ordersByPaymentMethod;
    }
}
