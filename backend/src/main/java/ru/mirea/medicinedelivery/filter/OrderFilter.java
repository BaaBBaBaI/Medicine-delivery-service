package ru.mirea.medicinedelivery.filter;

import ru.mirea.medicinedelivery.domain.DeliveryPriority;
import ru.mirea.medicinedelivery.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

/** Типобезопасный результат разбора поисковой строки и фильтров заказов. */
public record OrderFilter(
        String freeText,
        String customer,
        OrderStatus status,
        Boolean prescriptionRequired,
        DeliveryPriority priority,
        String tag,
        Boolean archived,
        List<AmountConstraint> amounts) {

    public enum Operator { LT, LTE, EQ, GTE, GT }
    public record AmountConstraint(Operator operator, BigDecimal value) { }
}
