package ru.mirea.medicinedelivery.service;

import org.springframework.stereotype.Component;
import ru.mirea.medicinedelivery.domain.MedicineOrder;
import ru.mirea.medicinedelivery.dto.OrderDtos.OrderResponse;

import java.util.Set;

/** Преобразует сущность заказа в публичный DTO внутри активной транзакции. */
@Component
public class MedicineOrderMapper {
    public OrderResponse toResponse(MedicineOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getCustomer() == null ? null : order.getCustomer().getId(),
                order.getCustomer() == null ? null : order.getCustomer().getCode(),
                order.getCustomer() == null ? null : order.getCustomer().getFullName(),
                order.getMedicationSummary(),
                order.getDeliveryAddress(),
                order.getContactPhone(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.isPrescriptionRequired(),
                order.getPrescriptionNumber(),
                order.getDeliveryNotes(),
                order.getPriorityLevel(),
                order.getDeliveredAt(),
                order.isArchived(),
                order.getArchivedAt(),
                Set.copyOf(order.getTags()),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getVersion()
        );
    }
}
