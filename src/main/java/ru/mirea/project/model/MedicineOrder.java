package ru.mirea.project.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Основная сущность — Заказ доставки лекарств.
 */
public class MedicineOrder {
    private Long id;
    private String orderNumber;
    private Long clientId;
    private User client;
    private String deliveryAddress;
    private String contactPhone;
    private double totalAmount;
    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private boolean prescriptionRequired;
    private String prescriptionNumber;
    private String deliveryNotes;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;

    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public MedicineOrder() {
        this.status = OrderStatus.CREATED;
        this.paymentMethod = PaymentMethod.CARD_ONLINE;
        this.createdAt = LocalDateTime.now();
        this.prescriptionRequired = false;
    }

    public MedicineOrder(Long id, String orderNumber, Long clientId, String deliveryAddress,
                         String contactPhone, double totalAmount, OrderStatus status,
                         PaymentMethod paymentMethod, boolean prescriptionRequired,
                         String prescriptionNumber, String deliveryNotes,
                         LocalDateTime createdAt, LocalDateTime deliveredAt) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.clientId = clientId;
        this.deliveryAddress = deliveryAddress;
        this.contactPhone = contactPhone;
        this.totalAmount = totalAmount;
        this.status = status != null ? status : OrderStatus.CREATED;
        this.paymentMethod = paymentMethod != null ? paymentMethod : PaymentMethod.CARD_ONLINE;
        this.prescriptionRequired = prescriptionRequired;
        this.prescriptionNumber = prescriptionNumber;
        this.deliveryNotes = deliveryNotes;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.deliveredAt = deliveredAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public User getClient() {
        return client;
    }

    public void setClient(User client) {
        this.client = client;
        if (client != null && this.clientId == null) {
            this.clientId = client.getId();
        }
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public boolean isPrescriptionRequired() {
        return prescriptionRequired;
    }

    public void setPrescriptionRequired(boolean prescriptionRequired) {
        this.prescriptionRequired = prescriptionRequired;
    }

    public String getPrescriptionNumber() {
        return prescriptionNumber;
    }

    public void setPrescriptionNumber(String prescriptionNumber) {
        this.prescriptionNumber = prescriptionNumber;
    }

    public String getDeliveryNotes() {
        return deliveryNotes;
    }

    public void setDeliveryNotes(String deliveryNotes) {
        this.deliveryNotes = deliveryNotes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MedicineOrder that = (MedicineOrder) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        String clientInfo = (client != null) ? client.getFullName() : ("Клиент #" + clientId);
        return String.format("Заказ[%d | %s] Клиент: %s | Сумма: %.2f руб. | Статус: %s | Рецепт: %s | Создан: %s",
                id, orderNumber, clientInfo, totalAmount, status.getDisplayName(),
                prescriptionRequired ? ("ДА (" + prescriptionNumber + ")") : "НЕТ",
                createdAt != null ? createdAt.format(DATE_FORMATTER) : "—");
    }
}
