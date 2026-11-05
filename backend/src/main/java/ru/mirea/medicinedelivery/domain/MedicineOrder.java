package ru.mirea.medicinedelivery.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Основная доменная сущность: заказ доставки лекарств (из КР №1 и КР №2).
 *
 * <p>Включает номер заказа, связь со справочником клиентов аптечной сети, состав заказа,
 * адрес и телефон доставки, сумму, статус, способ оплаты, признак и номер рецепта,
 * вычисляемый приоритет доставки, теги, контроль версий и поддержку архивирования.</p>
 */
@Entity
@Table(name = "medicine_order")
public class MedicineOrder extends AuditedVersionedEntity {
    @Id
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 100)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "medication_summary", nullable = false, length = 500)
    private String medicationSummary;

    @Column(name = "delivery_address", nullable = false, length = 255)
    private String deliveryAddress;

    @Column(name = "contact_phone", nullable = false, length = 32)
    private String contactPhone;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status = OrderStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private PaymentMethod paymentMethod = PaymentMethod.CARD_ONLINE;

    @Column(name = "prescription_required", nullable = false)
    private boolean prescriptionRequired;

    @Column(name = "prescription_number", length = 60)
    private String prescriptionNumber;

    @Column(name = "delivery_notes", length = 1000)
    private String deliveryNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_level", nullable = false, length = 32)
    private DeliveryPriority priorityLevel = DeliveryPriority.LOW;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "archived_by")
    private UserAccount archivedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private UserAccount createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private UserAccount updatedBy;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "medicine_order_tag", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "tag", nullable = false, length = 50)
    private Set<String> tags = new LinkedHashSet<>();

    protected MedicineOrder() {
    }

    public MedicineOrder(UUID id, String orderNumber, String medicationSummary,
                         String deliveryAddress, String contactPhone,
                         BigDecimal totalAmount, UserAccount creator) {
        this.id = id;
        this.orderNumber = orderNumber;
        this.medicationSummary = medicationSummary;
        this.deliveryAddress = deliveryAddress;
        this.contactPhone = contactPhone;
        this.totalAmount = totalAmount;
        this.createdBy = creator;
        this.updatedBy = creator;
    }

    public UUID getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public String getMedicationSummary() { return medicationSummary; }
    public void setMedicationSummary(String medicationSummary) { this.medicationSummary = medicationSummary; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }
    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public boolean isPrescriptionRequired() { return prescriptionRequired; }
    public void setPrescriptionRequired(boolean prescriptionRequired) { this.prescriptionRequired = prescriptionRequired; }
    public String getPrescriptionNumber() { return prescriptionNumber; }
    public void setPrescriptionNumber(String prescriptionNumber) { this.prescriptionNumber = prescriptionNumber; }
    public String getDeliveryNotes() { return deliveryNotes; }
    public void setDeliveryNotes(String deliveryNotes) { this.deliveryNotes = deliveryNotes; }
    public DeliveryPriority getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(DeliveryPriority priorityLevel) { this.priorityLevel = priorityLevel; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getArchivedAt() { return archivedAt; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
    public UserAccount getArchivedBy() { return archivedBy; }
    public void setArchivedBy(UserAccount archivedBy) { this.archivedBy = archivedBy; }
    public UserAccount getCreatedBy() { return createdBy; }
    public UserAccount getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UserAccount updatedBy) { this.updatedBy = updatedBy; }
    public Set<String> getTags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = new LinkedHashSet<>(tags); }
}
