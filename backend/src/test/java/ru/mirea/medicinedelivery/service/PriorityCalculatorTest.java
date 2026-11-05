package ru.mirea.medicinedelivery.service;

import org.junit.jupiter.api.Test;
import ru.mirea.medicinedelivery.domain.DeliveryPriority;
import ru.mirea.medicinedelivery.domain.OrderStatus;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2. Тест бизнес-правил расчёта вычисляемого показателя приоритета доставки (DeliveryPriority).
 */
class PriorityCalculatorTest {
    private final PriorityCalculator calculator = new PriorityCalculator();

    @Test
    void prescriptionAndHighAmountIsCritical() {
        assertThat(calculator.calculate(OrderStatus.CREATED, new BigDecimal("3450.00"), true))
                .isEqualTo(DeliveryPriority.CRITICAL);
    }

    @Test
    void prescriptionWithSmallAmountIsHigh() {
        assertThat(calculator.calculate(OrderStatus.ASSEMBLING, new BigDecimal("850.00"), true))
                .isEqualTo(DeliveryPriority.HIGH);
    }

    @Test
    void nonPrescriptionModerateAmountIsMedium() {
        assertThat(calculator.calculate(OrderStatus.CONFIRMED, new BigDecimal("2100.00"), false))
                .isEqualTo(DeliveryPriority.MEDIUM);
    }

    @Test
    void nonPrescriptionSmallAmountIsLow() {
        assertThat(calculator.calculate(OrderStatus.CREATED, new BigDecimal("650.00"), false))
                .isEqualTo(DeliveryPriority.LOW);
    }

    @Test
    void deliveredOrCancelledOrderIsNotApplicable() {
        assertThat(calculator.calculate(OrderStatus.DELIVERED, new BigDecimal("6800.00"), true))
                .isEqualTo(DeliveryPriority.NOT_APPLICABLE);
        assertThat(calculator.calculate(OrderStatus.CANCELLED, new BigDecimal("4200.00"), true))
                .isEqualTo(DeliveryPriority.NOT_APPLICABLE);
    }
}
