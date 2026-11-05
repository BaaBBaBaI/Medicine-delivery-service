package ru.mirea.medicinedelivery.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.mirea.medicinedelivery.domain.DeliveryPriority;
import ru.mirea.medicinedelivery.domain.OrderStatus;
import ru.mirea.medicinedelivery.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** DTO-контракты основной сущности (заказов доставки лекарств) и массовых операций. */
public final class OrderDtos {
    private OrderDtos() {
    }

    /** Входная модель создания и изменения заказа доставки лекарств. */
    public record OrderRequest(
            @Size(max = 100) String orderNumber,
            UUID customerId,
            @NotBlank @Size(max = 500) String medicationSummary,
            @NotBlank @Size(max = 255) String deliveryAddress,
            @NotBlank @Pattern(regexp = "^\\+7-\\d{3}-\\d{3}-\\d{2}-\\d{2}$", message = "телефон должен быть в формате +7-999-999-99-99") String contactPhone,
            @NotNull @DecimalMin(value = "0.01", message = "сумма заказа должна быть больше 0") @DecimalMax("1000000.00") BigDecimal totalAmount,
            @NotNull OrderStatus status,
            @NotNull PaymentMethod paymentMethod,
            boolean prescriptionRequired,
            @Size(max = 60) String prescriptionNumber,
            @Size(max = 1000) String deliveryNotes,
            @Size(max = 20) Set<@NotBlank @Size(max = 50) String> tags,
            Long version) {
    }

    /** Выходная карточка заказа доставки лекарств. */
    public record OrderResponse(
            UUID id,
            String orderNumber,
            UUID customerId,
            String customerCode,
            String customerName,
            String medicationSummary,
            String deliveryAddress,
            String contactPhone,
            BigDecimal totalAmount,
            OrderStatus status,
            PaymentMethod paymentMethod,
            boolean prescriptionRequired,
            String prescriptionNumber,
            String deliveryNotes,
            DeliveryPriority priorityLevel,
            Instant deliveredAt,
            boolean archived,
            Instant archivedAt,
            Set<String> tags,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    /** Поддерживаемые виды массовых операций над заказами. */
    public enum BulkAction { ARCHIVE, RESTORE, SET_STATUS, ADD_TAG, REMOVE_TAG }

    /** Запрос массовой операции по набору UUID. */
    public record BulkRequest(
            @NotNull @Size(min = 1, max = 500) Set<UUID> ids,
            @NotNull BulkAction action,
            OrderStatus status,
            @Size(max = 50) String tag) {
    }

    /** Итог массовой операции с раздельным учётом успешных изменений и ошибок. */
    public record BulkResult(int requested, int succeeded, List<BulkFailure> failures) {
    }

    /** Ошибка обработки конкретного UUID при массовой операции. */
    public record BulkFailure(UUID id, String message) {
    }
}
