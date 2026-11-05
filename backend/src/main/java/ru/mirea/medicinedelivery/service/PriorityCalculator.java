package ru.mirea.medicinedelivery.service;

import org.springframework.stereotype.Component;
import ru.mirea.medicinedelivery.domain.DeliveryPriority;
import ru.mirea.medicinedelivery.domain.OrderStatus;

import java.math.BigDecimal;

/**
 * Единообразно рассчитывает приоритет доставки и фармацевтического контроля заказа.
 */
@Component
public class PriorityCalculator {

    /**
     * Вычисляет уровень приоритета доставки:
     * <ul>
     *   <li>Для уже доставленных ({@code DELIVERED}) или отменённых ({@code CANCELLED}) заказов
     *       возвращает {@link DeliveryPriority#NOT_APPLICABLE};</li>
     *   <li>Для рецептурных заказов стоимостью от 3000 руб. (или без суммы) — {@link DeliveryPriority#CRITICAL};</li>
     *   <li>Для остальных рецептурных заказов либо заказов стоимостью от 5000 руб. — {@link DeliveryPriority#HIGH};</li>
     *   <li>Для безрецептурных заказов стоимостью от 1500 до 4999.99 руб. — {@link DeliveryPriority#MEDIUM};</li>
     *   <li>Для небольших безрецептурных заказов до 1500 руб. — {@link DeliveryPriority#LOW}.</li>
     * </ul>
     */
    public DeliveryPriority calculate(OrderStatus status, BigDecimal totalAmount, boolean prescriptionRequired) {
        if (status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED) {
            return DeliveryPriority.NOT_APPLICABLE;
        }
        if (prescriptionRequired && (totalAmount == null || totalAmount.compareTo(new BigDecimal("3000.00")) >= 0)) {
            return DeliveryPriority.CRITICAL;
        }
        if (prescriptionRequired || (totalAmount != null && totalAmount.compareTo(new BigDecimal("5000.00")) >= 0)) {
            return DeliveryPriority.HIGH;
        }
        if (totalAmount == null || totalAmount.compareTo(new BigDecimal("1500.00")) >= 0) {
            return DeliveryPriority.MEDIUM;
        }
        return DeliveryPriority.LOW;
    }
}
